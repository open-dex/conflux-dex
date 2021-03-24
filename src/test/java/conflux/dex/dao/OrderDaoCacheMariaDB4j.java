package conflux.dex.dao;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.springframework.MariaDB4jSpringService;
import conflux.dex.model.User;
import conflux.dex.tool.FileUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OrderDaoCacheMariaDB4j.Conf.class)
@TestPropertySource(locations="classpath:mariadb4j.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) //otherwise mariadb is not cleaned up between tests
@Ignore
public class OrderDaoCacheMariaDB4j {
    private static Logger logger = LoggerFactory.getLogger(OrderDaoCacheMariaDB4j.class);
    private static MariaDB4jSpringService DB;
    private long userId;

    /**
     * On macOS:
     * If an exception occur: dyld: Library not loaded: /usr/local/opt/openssl/lib/libssl.1.0.0.dylib,
     * Just run :
     * sudo ln -s /usr/lib/libcrypto.dylib /usr/local/opt/openssl/lib/libcrypto.1.0.0.dylib
     * sudo ln -s /usr/lib/libssl.dylib /usr/local/opt/openssl/lib/libssl.1.0.0.dylib
     * @throws ManagedProcessException
     */
    @BeforeClass
    public static void init() throws ManagedProcessException {
        String dbName = "conflux_dex";
        String sql;
        try {
            sql = FileUtil.buildTempSql("initdb.sql","__DATABASE_NAME", dbName);
        } catch (Exception e) {
            logger.error("build sql fail.", e);
            Assert.fail("Can not build sql.");
            return;
        }
        //
        DB = new MariaDB4jSpringService();
        DB.setDefaultPort(1234);
        DB.start();
        //Create our database with default root user and no password
        DB.getDB().createDB(dbName);
        DB.getDB().run(sql);
        DB.getDB().source("test_data.sql");
    }
    @EnableCaching
    @SpringBootApplication
    static class Conf{
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
    @Autowired
    CacheManager cacheManager;

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    ProductDao productDao;
    @Autowired
    UserDao userDao;
    @Autowired
    OrderDao orderDao;

    @Before
    public void initData() {
        User user = new User();
        user.setName("testUser"+System.currentTimeMillis());
        boolean b = userDao.addUser(user);
        Assert.assertTrue("Add user fail", b);
        this.userId = user.getId();
        logger.info(" user id is "+userId);
    }

    @Test()
    public void testMysqlDialect() {
        jdbcTemplate.execute("select 1");
        try {
            jdbcTemplate.execute("insert ignore into not_exists values(1)");
        } catch(BadSqlGrammarException e) {
            logger.error("", e);
            Assert.assertTrue(e.getMessage().contains("not_exists' doesn't exist"));
        }
    }

    @Test
    public void testCache() {
        OrderDaoSpringTest test = new OrderDaoSpringTest();
        test.orderDao = this.orderDao;
        test.userId = userId;
        test.cacheManager = cacheManager;
        test.testCache();
    }

    @AfterClass
    public static void cleanup() {
        if (DB != null) DB.stop();
    }
}
