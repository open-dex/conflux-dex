package conflux.dex.dao;

import conflux.dex.model.InstantExchangeProduct;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.cache.Cache;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.Arrays;

@ContextConfiguration(classes = SetupCacheStub.class)
@RunWith(SpringRunner.class)
public class ProductDaoCacheTest extends DaoTestBase<ProductDao>{
    @Test
    public void testCache() {
        String cacheName = "dao.product";
        int id = 1;
        InstantExchangeProduct bean = new InstantExchangeProduct();
        String name = "test";
        bean.setName(name);
        //
        Mockito.when(jdbcTemplate.query(Mockito.anyString(),
                Mockito.any(RowMapper.class), Mockito.anyLong())).thenReturn(Arrays.asList(bean));
        // mock insert
        mockInsert(id);
        // mock update
        Mockito.when(jdbcTemplate.update(
                Mockito.any(),
                 Mockito.any(), Mockito.any(), Mockito.any()
                , Mockito.any(), Mockito.any(), Mockito.any()
                , Mockito.any()
                )).then(answer->{
                    logger.info("call it");
                    return 10;
        });
        //
        int update = jdbcTemplate.update("", 1, 2, 3, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                0);
        //
        triggerCache(id, name);
        // check cache
        Cache cache = cacheManager.getCache(cacheName);
        checkCache(id, name, cache);

        // trigger evict
        dao.addProduct(bean);
        checkEvict(id, name, cache);

        //
        triggerCache(id, name);
        checkCache(id, name, cache);
        dao.addInstantExchangeProduct(bean);
        checkEvict(id, name, cache);

        //
        triggerCache(id, name);
        checkCache(id, name, cache);
        boolean b = dao.updateProduct(bean);
        Assert.assertTrue("should updated", b);
        checkEvict(id, name, cache);
    }

    public void checkCache(int id, String name, Cache cache) {
        Assert.assertNotNull("should cache list", cache.get("list"));
        Assert.assertNotNull("should cache by id", cache.get(id));
        Assert.assertNotNull("should cache by name", cache.get(name));
        Assert.assertNotNull("should cache list by currency id", cache.get(id));
    }

    public void checkEvict(int id, String name, Cache cache) {
        // check evict
        Assert.assertNull("should evict list", cache.get("list"));
        Assert.assertNull("should evict by id", cache.get(id));
        Assert.assertNull("should evict by name", cache.get(name));
        Assert.assertNull("should evict list by currency id", cache.get(id+"listByCurrencyId"));
    }

    public void triggerCache(int id, String name) {
        // trigger cache
        dao.listProducts();
        dao.getProduct(id);
        dao.getProductByName(name);
        dao.listProductsByCurrencyId(id);
    }
}
