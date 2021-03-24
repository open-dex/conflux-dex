package conflux.dex.dao;

import conflux.dex.model.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.OrderType;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * https://www.baeldung.com/spring-testing-separate-data-source
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = OrderDaoSpringTest.OrderTestConf.class)
@Transactional
@Rollback
// It connects to real database, ignore in case  one doesn't have one.
// You can still run it manually.
@Ignore
public class OrderDaoSpringTest {
    @EnableCaching
    @SpringBootApplication
    static class OrderTestConf {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    CacheManager cacheManager;
    @Autowired
    OrderDao orderDao;
    long userId = 1;

    @Test
    public void testCache() {
        Order order = buildOrder(userId);
        //
        boolean b = orderDao.addOrder(order);
        Assert.assertTrue("insert failed", b);
        long id = order.getId();
        String cacheName = "dao.order";

        // trigger cache
        orderDao.mustGetOrder(id);
        Cache cache = cacheManager.getCache(cacheName);
        Assert.assertNotNull("should cached", cache.get(id));

        // trigger evict
        orderDao.fillOrder(id, BigDecimal.ONE, BigDecimal.ONE);
        Assert.assertNull(cache.get(id));
        //
        // trigger evict
        orderDao.updateOrderStatus(id, order.getStatus(), OrderStatus.Pending);
        Assert.assertNull("should evict", cache.get(id));
    }

    public static Order buildOrder(long userId) {
        Order order = new Order();
        order.setFilledAmount(BigDecimal.ONE);
        order.setFilledFunds(BigDecimal.ZERO);
        order.setStatus(OrderStatus.Open);
        order.setType(OrderType.Limit);
        order.setSide(OrderSide.Buy);
        order.setProductId(1);
        order.setPrice(BigDecimal.ONE);
        order.setFeeAddress("");
        order.setHash(String.valueOf(System.currentTimeMillis()));
        order.setSignature("");
        order.setUserId(userId);
        return order;
    }
}