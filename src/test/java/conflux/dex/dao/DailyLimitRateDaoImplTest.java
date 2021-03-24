package conflux.dex.dao;

import conflux.dex.model.DailyLimitRate;
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

import java.math.BigDecimal;
import java.util.Arrays;

@ContextConfiguration(classes = SetupCacheStub.class)
@RunWith(SpringRunner.class)
public class DailyLimitRateDaoImplTest extends DaoTestBase<DailyLimitRateDao>{
    private final DailyLimitRateDaoImpl dailyLimitRateDao = new DailyLimitRateDaoImpl();
    @Autowired
    DailyLimitRateDao dao;

    @Test(expected = NullPointerException.class)
    public void testAddDailyLimitRate() {
        DailyLimitRate dailyLimitRate = EasyMock.createMock(DailyLimitRate.class);
        EasyMock.expect(dailyLimitRate.getProductId()).andReturn(1);
        EasyMock.expect(dailyLimitRate.getInitialPrice()).andReturn(BigDecimal.valueOf(1L));
        EasyMock.expect(dailyLimitRate.getLowerLimitRate()).andReturn(1d);
        EasyMock.expect(dailyLimitRate.getUpperLimitRate()).andReturn(1d);

        EasyMock.replay(dailyLimitRate);
        dailyLimitRateDao.addDailyLimitRate(dailyLimitRate);
    }

    @Test(expected = NullPointerException.class)
    public void testGetDailyLimitRateByProductId() {
        dailyLimitRateDao.getDailyLimitRateByProductId(1);
    }

    @Test
    public void testCache() {
        String cacheName = "dao.daily-limit-rate";
        int productId = 1;

        DailyLimitRate bean = new DailyLimitRate();
        bean.setProductId(productId);
        //
        Mockito.when(jdbcTemplate.query(Mockito.anyString(),
                Mockito.any(RowMapper.class), Mockito.anyLong())).thenReturn(Arrays.asList(bean));
        // mock insert
        mockInsert(1);
        Cache cache = cacheManager.getCache(cacheName);

        triggerCache(productId);
        checkCache(productId, cache);

        // trigger evict
        dao.addDailyLimitRate(bean);
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
        dao.listDailyLimitRate();
        dao.getDailyLimitRateByProductId(productId);
    }
}
