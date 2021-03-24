package conflux.dex.dao;

import conflux.dex.model.Order;
import conflux.dex.model.OrderStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static conflux.dex.dao.OrderDaoSpringTest.buildOrder;

/**
 * Mock jdbc template to test database cache.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = OrderDaoCacheTest.TestConf.class)
@Ignore
public class OrderDaoCacheTest {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @TestConfiguration
    @EnableCaching
    static class TestConf {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        public JdbcTemplate jdbcTemplate() {
            return Mockito.mock(JdbcTemplate.class);
        }

        @Bean
        public DataSource dataSource() {
            return Mockito.mock(DataSource.class);
        }

        @Bean
        public OrderDao orderDao() {
            OrderDaoImpl orderDao = new OrderDaoImpl();
            return orderDao;
        }
    }

    @Autowired
    CacheManager cacheManager;

    @Autowired
    OrderDao orderDao;

    @Before
    public void init() {
        // Use mocked jdbc template.
        ReflectionTestUtils.setField(orderDao, "jdbcTemplate", jdbcTemplate);
        // mock update method
        Mockito.when(jdbcTemplate.update(Mockito.any(PreparedStatementCreator.class),
                Mockito.any(GeneratedKeyHolder.class))
        ).then((Answer<Integer>) invocation -> {
            // Fill auto generated key
            KeyHolder argument = invocation.getArgument(1, GeneratedKeyHolder.class);
            Map<String, Object> map = new HashMap<>(1);
            map.put("", 1);
            argument.getKeyList().add(map);
            // return modified/inserted row count
            return 1;
        });
    }

    @Test
    public void cacheTest() {
        Order order = buildOrder(1);

        // mock query(select), return a list contains order.
        Mockito.when(jdbcTemplate.query(Mockito.anyString(), Mockito.any(RowMapper.class), Mockito.any())
        ).thenReturn(Arrays.asList(order));

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

        // trigger cache
        Assert.assertNotNull("should find", orderDao.getOrder(id));
        Assert.assertNotNull("should cached again", cache.get(id));

        // trigger evict
        orderDao.updateOrderStatus(id, order.getStatus(), OrderStatus.Pending);
        Assert.assertNull("should evict", cache.get(id));
    }
}
