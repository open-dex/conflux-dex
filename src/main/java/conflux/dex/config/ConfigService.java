package conflux.dex.config;

import conflux.dex.dao.ConfigDao;
import conflux.dex.model.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dynamic configuration service, periodically loading configuration from the database.
 * How to use:
 * 1. Define field(s) in ConfigService (if needed), annotated by @Value as usual.
 * 2. Mark bean with @ConfigRefresh
 * 3. Call configService.hook(beanName, fieldNames) after bean creation (Autowire configService first).
 *
 * Note: if use standalone Class as config bean, then fields should have their getter/setter.
 */
@Component
public class ConfigService {

    Logger logger = LoggerFactory.getLogger(getClass());
    @Autowired
    private RefreshScope refreshScope;

    @Value("${blockchain.cfx.url}")
    volatile public String cfxUrl;

    @Value("${blockchain.cfx.retry:10}")
    volatile public int cfxRetry;

    @Value("${blockchain.cfx.intervalMillis:1000}")
    volatile public long cfxIntervalMillis;
    
    @Value("${blockchain.cfx.call.timeout.millis:3000}")
    volatile public long cfxCallTimeoutMillis;

    @Autowired
    private ConfigDao configDao;

    private Map<String, ConfigEntry> key2field = new HashMap<>();
    private Map<Class<?>, Function<String, Object>> typeResolver = new HashMap<>();
    private Map<String, Map<String, String>> field2beanHookMap = new ConcurrentHashMap<>();

    class ConfigEntry{
        Field field;
        Object container;
    }

    @PostConstruct
    public void init() {
        initTypeResolver();
        logger.info("Init config service.");
        // parse and cache field map.
        resolveConfig(this);
    }

    public List<Config> listAllConfig() {
        return configDao.listAll();
    }

    /**
     * Parse config holder.
     * @param configInstance
     */
    public synchronized void resolveConfig(Object configInstance) {
        Class<?> clazz = configInstance.getClass();
        Field[] fields = clazz.getFields();
        for (Field field : fields) {
            Value annotation = field.getAnnotation(Value.class);
            if (annotation == null) {
                continue;
            }
            Method setter = ReflectionUtils.findMethod(clazz, buildSetterName(field), field.getType());
            if (setter == null) {
                throw new RuntimeException("Config bean " + clazz.getSimpleName() +
                        " should have setter method for field " + field.getName());
            }
            field.setAccessible(true);
            String formula = annotation.value();
            // syntax parse , e.g. ${key:defaultValue} -> key
            formula = formula.substring(formula.indexOf("${") + 2, formula.lastIndexOf("}"));
            int idx = formula.indexOf(":");
            if (idx > 0) {
                formula = formula.substring(0, idx);
            }
            ConfigEntry entry = new ConfigEntry();
            entry.container = configInstance;
            entry.field = field;
            key2field.put(formula, entry);
            field2beanHookMap.put(field.getName(), new ConcurrentHashMap<>());
        }
        reload();
    }

    private String buildSetterName(Field field) {
        String name = field.getName();
        return "set"+name.substring(0, 1).toUpperCase().concat(name.substring(1));
    }

    private void initTypeResolver() {
        typeResolver.put(int.class, Integer::parseInt);
        typeResolver.put(Integer.class, Integer::parseInt);
        typeResolver.put(short.class, Short::parseShort);
        typeResolver.put(Short.class, Short::parseShort);
        typeResolver.put(long.class, Long::parseLong);
        typeResolver.put(Long.class, Long::parseLong);
        typeResolver.put(boolean.class, Boolean::valueOf);
        typeResolver.put(Boolean.class, Boolean::valueOf);
        typeResolver.put(String.class, s -> s);
        typeResolver.put(BigInteger.class, BigInteger::new);
        typeResolver.put(BigDecimal.class, BigDecimal::new);
    }

    public HashMap<String, Object> setConfig(String name, String value) {
        configDao.setConfig(name, value);
        return reload();
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 60_000)
    public synchronized HashMap<String, Object> reload() {
        HashMap<String, Object> message = new HashMap<>();
        reload(message);
        return message;
    }
    private void reload(Map<String, Object> message) {
        //
        if (key2field.isEmpty()) {
            logger.warn("fields map is empty.");
            return;
        }
        //load from database, then compare/override exists value.
        Map<String, Config> dbConfigMap = configDao
                .listConfig(key2field.keySet())
                .stream()
                .collect(Collectors.toMap(Config::getName, Function.identity()));
        //
        key2field.entrySet().forEach(entry -> {
            String key = entry.getKey();
            ConfigEntry configEntry = entry.getValue();
            replace(dbConfigMap, key, configEntry, message);
        });
    }

    private void replace(Map<String, Config> dbConfigMap, String key, ConfigEntry entry, Map<String, Object> message) {
        Field field = entry.field;
        Config config = dbConfigMap.get(key);
        String containerClz = entry.container.getClass().getSimpleName();
        if (config == null) {
            message.put(key, "not found");
            logger.debug("config not found for {} , field {} of {}", key, field.getName(), containerClz);
            return;
        } else if (config.getValue() == null) {
            message.put(key, "DB value is null");
            logger.warn("config is null for {} field {} of {}", key, field.getName(), containerClz);
            return;
        }
        try {
            Class<?> type = field.getType();
            Object newV;
            Function<String, Object> stringObjectFunction = typeResolver.get(type);
            if (stringObjectFunction == null) {
                message.put("success", false);
                message.put(key, "unsupported type");
                return;
            } else {
                newV = stringObjectFunction.apply(config.getValue());
            }
            //
            Object curFieldValue = field.get(entry.container);
            if (Objects.equals(curFieldValue, newV)) {
                message.put(key, "not change");
                logger.debug("not change {}", config.getName());
                return;
            }
//            field.set(entry.container, newV);
            BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(entry.container);
            bw.setPropertyValue(field.getName(), newV);
            message.put(key, "set value success");
            logger.info("set new value {}, pre {}, for key {} field {} of {}, hash {}",
                    config.getValue(), curFieldValue, config.getName(), field.getName(), containerClz, entry.container.hashCode());
            // refresh bean
            Map<String, String> hookedBean = field2beanHookMap.get(field.getName());
            hookedBean.keySet().forEach(beanName -> {
                try {
                    refreshScope.remove(RefreshScope.BEAN_NAME_PREFIX + beanName);
                    message.put(beanName, "replace bean ok");
                } catch (Exception e) {
                    Throwable root = e;
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    message.put(beanName, "replace bean fail. plz check log:"+root.toString());
                    message.put("success", false);
                    logger.error("replace bean fail.", e);
                }
            });
        } catch (IllegalAccessException e) {
            logger.error("error for {} field {} value {}, exception {}",
                    key, field.getName(), config.getValue(), e.toString());
        }
    }


    /**
     * Hook field name and bean name, in order to auto refresh bean when config was changed.
     * It should be done when creating bean.
     *
     * @param beanName
     * @param fieldNames according to field defined in this class
     */
    public synchronized void hook(String beanName, String... fieldNames) {
        for (String fieldName : fieldNames ) {
            hookFun(fieldName, beanName);
        }
    }

    private void hookFun(String fieldName, String beanName) {
        Map<String, String> beanMap = field2beanHookMap.get(fieldName);
        if (beanMap == null) {
            // very high level exception
            throw new RuntimeException("Unknown field name " + fieldName +
                    " , please check it.");
        }
        if (beanMap.containsKey(beanName)) {
            return;
        }
        beanMap.put(beanName, beanName);
        logger.info("Bean {} hooks field {} .", beanName, fieldName);
    }

    public void setCfxUrl(String cfxUrl) {
        this.cfxUrl = cfxUrl;
    }

    public void setCfxRetry(int cfxRetry) {
        this.cfxRetry = cfxRetry;
    }

    public void setCfxIntervalMillis(long cfxIntervalMillis) {
        this.cfxIntervalMillis = cfxIntervalMillis;
    }
    
    public void setCfxCallTimeoutMillis(long cfxCallTimeoutMillis) {
		this.cfxCallTimeoutMillis = cfxCallTimeoutMillis;
	}

    public void setConfigDao(ConfigDao configDao) {
        this.configDao = configDao;
    }

    public void setRefreshScope(RefreshScope refreshScope) {
        this.refreshScope = refreshScope;
    }
}
