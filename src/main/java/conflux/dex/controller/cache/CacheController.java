package conflux.dex.controller.cache;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import conflux.dex.config.AuthRequire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.ImmutableMap;

import conflux.dex.common.BusinessException;
import conflux.dex.controller.request.CacheOperation;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration;

/**
 * Helper to monitor cache.
 * @ignore
 */
@RestController
@RequestMapping("/cache/ehcache")
public class CacheController {
    private net.sf.ehcache.CacheManager cacheManager;
    @Value("${ui.admin.address}")
    private String adminAddress;

    @Autowired
    public void setCacheManagementService(CacheManager cacheManager) {
        // Getting the manager of EhCache
        EhCacheCacheManager cacheCacheManager = (EhCacheCacheManager) cacheManager;
        net.sf.ehcache.CacheManager ehCacheManager = cacheCacheManager.getCacheManager();
        this.cacheManager = ehCacheManager;
    }

    @GetMapping("/list")
    public Object listCaches() {
        Map<String, Object> cachesInfo = new HashMap<>();
        String[] names = this.cacheManager.getCacheNames();
        List<Map<String, Object>> list = Arrays.stream(names).map(name -> {
            Cache cache = cacheManager.getCache(name);
            Map<String, Object> infoMap = new HashMap<>();
            Status status = cache.getStatus();
            int size = cache.getSize();
            CacheConfiguration cacheConfiguration = cache.getCacheConfiguration();
            infoMap.put("status", status.toString());
            infoMap.put("size", size);
            infoMap.put("name", cacheConfiguration.getName());
            infoMap.put("isEternal", cacheConfiguration.isEternal());
            infoMap.put("maxEntriesInCache", cacheConfiguration.getMaxEntriesInCache());
            infoMap.put("maxEntriesLocalHeap", cacheConfiguration.getMaxEntriesLocalHeap());
            infoMap.put("persistence", Optional.ofNullable(cacheConfiguration)
                    .map(CacheConfiguration::getPersistenceConfiguration)
                    .map(PersistenceConfiguration::getStrategy)
                    .orElse(PersistenceConfiguration.Strategy.NONE)
            );
            infoMap.put("hasPersistenceConfiguration", cacheConfiguration.getPersistenceConfiguration() != null);

            return infoMap;
        }).collect(Collectors.toList());
        cachesInfo.put("caches", list);
        return cachesInfo;
    }

    @PostMapping("/get-quiet")
    public Object operate(@RequestBody CacheOperation<?> cacheOperation) {
        Cache cache = cacheManager.getCache(cacheOperation.getCacheName());
        if (cache == null) {
            throw BusinessException.validateFailed("Cache not found %s", cacheOperation.getCacheName());
        }
        String key = cacheOperation.getKey();
        Element element = cache.getQuiet(key);
        Element elementByInt = null;
        Element elementByLong = null;
        if (key.matches("\\d+")) {
            elementByInt = cache.getQuiet(Integer.valueOf(key));
            elementByLong = cache.getQuiet(Long.valueOf(key));
        }
        element = Stream.of(element, elementByInt, elementByLong)
                .filter(Objects::nonNull).findAny()
                .orElse(null);
        if (element == null) {
            throw BusinessException.validateFailed(
                    String.format("Not found in cache %s key %s",
                    cacheOperation.getCacheName(), key)
            );
        }
        return ImmutableMap.builder()
                .put("value", Optional.ofNullable(element.getObjectValue()).orElse("null"))
                .put("isEternal", element.isEternal())
                .put("isExpired", element.isExpired())
                .put("creationTime", element.getCreationTime())
                .put("expirationTime", element.getExpirationTime())
                .build();
    }

    @AuthRequire
    @PostMapping("/remove")
    public Object remove(@RequestBody CacheOperation<?> cacheOperation) {
        Cache cache = cacheManager.getCache(cacheOperation.getCacheName());
        if (cache == null) {
            throw BusinessException.validateFailed(
                    String.format("Cache not found %s", cacheOperation.getCacheName() )
            );
        }
        boolean removed = cache.remove(cacheOperation.getKey());
        return removed;
    }

    @AuthRequire
    @GetMapping("/keys/{name}")
    public Object load(@PathVariable String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache == null) {
            throw BusinessException.validateFailed(
                    String.format("Cache not found %s", name)
            );
        }
        // Since we don't use off heap cache, keys() method takes very short time.
        List<?> keyList = cache.getKeys();
        List<String> stringList = keyList.stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        return stringList;
    }
}
