package conflux.dex.dao;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.model.Currency;
import conflux.dex.model.InstantExchangeProduct;
import conflux.web3j.types.Address;
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
public class CurrencyDaoCacheTest extends DaoTestBase<CurrencyDao>{
    @Test
    public void testCache() {
        String cacheName = "dao.currency";
        int id = 1;
        Domain.defaultChainId = Address.NETWORK_ID_TESTNET;
        String address = "0x84e0ec6eb1d85d999629b3ae35ad874ff870f939";
        String name = "test";
        Currency bean = new Currency();
        bean.setMinimumWithdrawAmount(BigDecimal.ONE);
        bean.setContractAddress(address);
        bean.setName(name);
        bean.setCrossChain(false);
        bean.setTokenAddress(address);
        //
        Mockito.when(jdbcTemplate.query(Mockito.anyString(),
                Mockito.any(RowMapper.class), Mockito.anyLong())).thenReturn(Arrays.asList(bean));
        // mock insert
        mockInsert(id);
        // mock update
        Mockito.when(jdbcTemplate.update(Mockito.anyString(),
        		Mockito.anyString(), Mockito.any(BigDecimal.class), Mockito.anyInt()))
                .thenReturn(1);
        //
        dao.addCurrency(bean);
        //
        triggerCache(id, name, address);
        // check cache
        Cache cache = cacheManager.getCache(cacheName);
        checkCache(bean, cache);

        // trigger evict
        dao.addCurrency(bean);
        checkEvict(id, name, address, cache);

        //
        triggerCache(id, name, address);
        checkCache(bean, cache);
        boolean b = dao.updateCurrency(bean);
        Assert.assertTrue("should updated", b);
        checkEvict(id, name, address, cache);
    }

    public void checkCache(Currency c, Cache cache) {
        Assert.assertNotNull("should cache list", cache.get("list"));
        Assert.assertNotNull("should cache by id", cache.get(c.getId()));
        Assert.assertNotNull("should cache by name", cache.get(c.getName()));
        Assert.assertNotNull("should cache list by address id", cache.get(c.getContractAddress()));
    }

    public void checkEvict(int id, String name, String address, Cache cache) {
        // check evict
        Assert.assertNull("should evict list", cache.get("list"));
        Assert.assertNull("should evict by id", cache.get(id));
        Assert.assertNull("should evict by name", cache.get(name));
        Assert.assertNull("should evict list by address", cache.get(address));
    }

    public void triggerCache(int id, String name, String address) {
        // trigger cache
        dao.getCurrencyByName(name);
        dao.getCurrency(id);
        dao.getCurrencyByContractAddress(address);
        dao.listCurrencies();
    }
}
