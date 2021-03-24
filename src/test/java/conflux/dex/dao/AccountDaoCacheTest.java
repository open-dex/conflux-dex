package conflux.dex.dao;

import conflux.dex.model.Account;
import conflux.dex.model.AccountStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

@ContextConfiguration(classes = SetupCacheStub.class)
@RunWith(SpringRunner.class)
@Ignore
public class AccountDaoCacheTest {
    @Autowired
    CacheManager cacheManager;
    @Autowired
    AccountDao accountDao;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Before
    public void init() {
        // Use mocked jdbc template.
        ReflectionTestUtils.setField(accountDao, "jdbcTemplate", jdbcTemplate);
    }
    @Test
    public void testCache() {
        //
        String cacheName = "dao.account";
        String cacheNameList = "dao.account.list";
        //
        long id = 1;
        long userId = 2;
        String currency = "CNY";
        // build account
        Account account = new Account();
        account.setId(id);
        account.setUserId(userId);
        account.setCurrency(currency);
        // setup mock behavior
        // query by id or user id
        Mockito.when(jdbcTemplate.query(Mockito.anyString(),
                Mockito.any(RowMapper.class), Mockito.anyLong())).thenReturn(Arrays.asList(account));
        // query by user id and currency
        Mockito.when(jdbcTemplate.query(Mockito.anyString(),
                Mockito.any(RowMapper.class), Mockito.anyLong(), Mockito.anyString()))
                .thenReturn(Arrays.asList(account));
        //
        triggerCache(cacheName, cacheNameList, id, userId, currency);
        //
        // trigger evict
        accountDao.updateAccountStatus(id, AccountStatus.Normal, AccountStatus.ForceWithdrawing);
        checkEvict(cacheName, cacheNameList, id, userId, currency);

        //
        triggerCache(cacheName, cacheNameList, id, userId, currency);
        accountDao.addAccount(account);
        checkEvict(cacheName, cacheNameList, id, userId, currency);
    }

    public void checkEvict(String cacheName, String cacheNameList, long id, long userId, String currency) {
        Assert.assertNull("should evict by id",  cacheManager.getCache(cacheName).get(id));
        Assert.assertNull("should evict list",  cacheManager.getCache(cacheNameList).get(id));
        Assert.assertNull("should evict by user id and currency",
                cacheManager.getCache(cacheName).get(new SimpleKey(userId, currency)));
    }

    public void triggerCache(String cacheName, String cacheNameList, long id, long userId, String currency) {
        // trigger cache
        accountDao.getAccountById(id);
        Assert.assertNotNull("should cache by id", cacheManager.getCache(cacheName).get(id));
        accountDao.getAccount(userId, currency);
        Assert.assertNotNull("should cache by user id and currency",
                cacheManager.getCache(cacheName).get(new SimpleKey(userId, currency)));
        accountDao.listAccounts(userId);
        Assert.assertNotNull("should cache list", cacheManager.getCache(cacheNameList).get(userId));
    }
}
