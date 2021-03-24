package conflux.dex.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * see https://www.baeldung.com/spring-custom-scope
 */
@Component
public class RefreshScope implements Scope, BeanFactoryPostProcessor {
    public static final String BEAN_NAME_PREFIX = "scopedTarget.";
    Logger logger = LoggerFactory.getLogger(getClass());

    Map<String, Object> scopedObjects = new ConcurrentHashMap<>();
    Map<String, ObjectFactory<?>> beanFactoryMap = new ConcurrentHashMap<>();
    Map<String, Object> destructionCallbacks = new ConcurrentHashMap<>();


    /**
     * Attention, this interface conflict with PostConstruct annotation(prevent from executing).
     * @param beanFactory
     * @throws BeansException
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        beanFactory.registerScope(ConfigRefresh.SCOPE_NAME, this);
        logger.info("register config refresh scope.");
    }

    /**
     * Reference to Bean with annotation ConfigRefresh will be delegated to here.
     * @param name with prefix scopedTarget.
     * @param objectFactory
     * @return
     */
    @Override
    public synchronized Object get(String name, ObjectFactory<?> objectFactory) {
        logger.debug("scope get {}", name);
        Object obj = scopedObjects.get(name);
        if(obj == null) {
            obj = objectFactory.getObject();
            scopedObjects.put(name, obj);
            beanFactoryMap.put(name, objectFactory);
        }
        return obj;
    }

    @Override
    public synchronized Object remove(String name) {
        // try create, in case creation will fail.
        Object newOne;
        try {
            newOne = beanFactoryMap.get(name).getObject();
        } catch (Exception e) {
            throw new RuntimeException("Create bean fail", e);
        }
        //remove when successfully created.
        logger.info("remove {}", name);
        destructionCallbacks.remove(name);
        Object pre = scopedObjects.remove(name);

        scopedObjects.put(name, newOne);
        return pre;
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        destructionCallbacks.put(name, callback);
    }

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        return getClass().getName();
    }

    // 省略***
}
