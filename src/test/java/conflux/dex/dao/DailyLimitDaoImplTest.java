package conflux.dex.dao;

import conflux.dex.model.DailyLimit;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;

@ContextConfiguration(classes = SetupCacheStub.class)
@RunWith(SpringRunner.class)
public class DailyLimitDaoImplTest  extends DaoTestBase<DailyLimitDao>{
    private final DailyLimitDaoImpl dailyLimitDao = new DailyLimitDaoImpl();
    @Autowired DailyLimitDao dao;

    @Test(expected = NullPointerException.class)
    public void testGetCurrency() {
        DailyLimit dailyLimit = EasyMock.createMock(DailyLimit.class);
        dailyLimitDao.addDailyLimit(dailyLimit);
    }

    @Test(expected = NullPointerException.class)
    public void testListDailyLimitsByProductId() {
        dailyLimitDao.listDailyLimitsByProductId(1);
    }

    @Test
    public void testCache() {
        String cacheName = "dao.daily-limit";
        int productId = 1;

        DailyLimit bean = new DailyLimit();
        bean.setProductId(productId);
        //
        Mockito.when(jdbcTemplate.query(Mockito.anyString(),
                Mockito.any(RowMapper.class), Mockito.anyLong())).thenReturn(Arrays.asList(bean));
        // mock insert
        mockInsert(1);
        Cache cache = cacheManager.getCache(cacheName);
        //

        triggerCache(productId);
        checkCache(productId, cache);

        // trigger evict
        dao.addDailyLimit(bean);
        checkEvict(productId, cache);

        triggerCache(productId);
        checkCache(productId, cache);

        // trigger evict.
        dao.removeDailyLimit(productId);
        checkEvict(productId, cache);
    }

    public void checkEvict(int id, Cache cache) {
        // check evict
        Assert.assertNull("should evict list", cache.get("list"));
        Assert.assertNull("should evict by id", cache.get(id));
    }


    public void checkCache(int productId, Cache cache) {
        Assert.assertNotNull("should cache list", cache.get("list"));
        Assert.assertNotNull("should cache by id", cache.get(productId));
    }

    private void triggerCache(int productId) {
        dao.listAllDailyLimit();
        dao.listDailyLimitsByProductId(productId);
    }
}
