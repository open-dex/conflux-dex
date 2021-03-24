package conflux.dex.config.qps;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conflux.dex.dao.ConfigDao;
import conflux.dex.dao.QPSConfDao;

@Component
public class QPSConfigService {
    
    public static final String ONLY_WHITELIST = "ONLY_WHITELIST";
    public static final String FILTER_MODE_KEY = "QPS_FILTER_MODE";
    public static final String FILTER_MODE_NONE = "NONE";
    private ConcurrentHashMap<String, LimitedResource> configCache = new ConcurrentHashMap<>();
    private ConfigDao configDao;
    private String filterMode;
    private QPSConfDao qpsConfDao;

    @PostConstruct
    public void init() {
        this.filterMode = configDao
                .getConfig(FILTER_MODE_KEY)
                .orElse(FILTER_MODE_NONE);
        qpsConfDao.listAll().forEach(c->configCache.put(c.key, c));
    }

    @Autowired
    public void setConfigDao(ConfigDao configDao, QPSConfDao qpsConfDao) {
        this.configDao = configDao;
        this.qpsConfDao = qpsConfDao;
    }

    public LimitedResource getConf(String key) {
        if (key == null) {
            return null;
        }
        return configCache.get(key);
    }

    public LimitedResource setRate(String key, double rate) {
        LimitedResource conf = configCache.computeIfAbsent(key, k -> LimitedResource.buildResource(key));
        conf.rate = rate;
        qpsConfDao.save(conf);
        return conf;
    }

    public Collection<LimitedResource> list() {
        return configCache.values();
    }

    public int delete(LimitedResource bean) {
        configCache.remove(bean.key);
        return qpsConfDao.delete(bean.id);
    }



    public String getFilterMode() {
        return filterMode;
    }
    public void setFilterMode(String mode) {
        configDao.setConfig(FILTER_MODE_KEY, mode);
        filterMode = mode;
    }

    public boolean isWhitelistMode() {
        return ONLY_WHITELIST.equals(filterMode);
    }
}
