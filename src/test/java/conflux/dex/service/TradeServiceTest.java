package conflux.dex.service;

import conflux.dex.common.BusinessFault;
import conflux.dex.dao.DexDao;
import conflux.dex.dao.EntityGetResult;
import conflux.dex.model.Product;
import conflux.dex.model.Trade;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TradeServiceTest {
    TradeService tradeService;
    DexDao dexDao;
    Product product1;
    Product product2;
    @Before
    public void init() {
        //
        product1 = buildProduct(1);
        product2 = buildProduct(2);
    }

    private Product buildProduct(int idAndName) {
        Product p = new Product();
        p.setName(String.valueOf(idAndName));
        p.setId(idAndName);
        return p;
    }

    @Test
    public void whenDBisEmpty() {
        dexDao = Mockito.mock(DexDao.class);
        // when db is empty
        Mockito.when(dexDao
                .listRecentTrades(Mockito.eq(1), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(Collections.EMPTY_LIST);
        Mockito.when(dexDao.getProductByName("1")).thenReturn(EntityGetResult.of(Arrays.asList(product1), BusinessFault.ProductNotFound));
        Mockito.when(dexDao.getProduct(Mockito.anyInt())).thenReturn(EntityGetResult.of(Arrays.asList(product1), BusinessFault.ProductNotFound));

        tradeService = new TradeService(dexDao);

        List<Trade> latest = tradeService.getLatest(1, 10);
        Assert.assertTrue("should be empty", latest.isEmpty());

        // Add trade
        Trade trade1 = buildTrade(1, 1);
        tradeService.addTradeCache(trade1);
        Assert.assertEquals("should have one trade", trade1.getId(), tradeService.getLatest(1, 10).get(0).getId());

        // another trade
        Trade trade2 = buildTrade(1, 2);
        tradeService.addTradeCache(trade2);
        Assert.assertEquals("should have two trades", 2, tradeService.getLatest(1, 10).size());
        List<Trade> trades = tradeService.getLatest(1, 10);
        Assert.assertTrue("should be in descending order",
                trades.get(0).getId() > trades.get(1).getId());
    }

    @Test
    public void whenDBHasEntries() {
        dexDao = Mockito.mock(DexDao.class);
        // when db is empty
        Mockito.when(dexDao
                .listRecentTrades(Mockito.eq(1), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(Arrays.asList(buildTrade(1, 1), buildTrade(1, 2)));
        Mockito.when(dexDao.getProductByName("1")).thenReturn(EntityGetResult.of(Arrays.asList(product1), BusinessFault.ProductNotFound));
        Mockito.when(dexDao.getProduct(Mockito.anyInt())).thenReturn(EntityGetResult.of(Arrays.asList(product1), BusinessFault.ProductNotFound));

        tradeService = new TradeService(dexDao);

        List<Trade> latest = tradeService.getLatest(1, 10);
        Assert.assertTrue("should have two", latest.size() == 2);

        // Add trade
        Trade trade3 = buildTrade(1, 3);
        tradeService.addTradeCache(trade3);
        Assert.assertEquals("should have the 3rd trade", trade3.getId(),
                tradeService.getLatest(1, 10).get(0).getId());

        // test entries over limit
        for (int i = 0; i < TradeService.MAX_LIST_SIZE; i++) {
            tradeService.addTradeCache(buildTrade(1, i + 1000));
        }
        List<Trade> latestAll = tradeService.getLatest(1, TradeService.MAX_LIST_SIZE+8);
        Assert.assertEquals("should have right size", TradeService.MAX_LIST_SIZE, latestAll.size() );
        Assert.assertEquals("should evict oldest", 1000,
                latestAll.get(latestAll.size()-1).getId());
        //
        tradeService.addTradeCache(buildTrade(1, 2001));
        latestAll = tradeService.getLatest(1, TradeService.MAX_LIST_SIZE+8);
        Assert.assertEquals("should have right size", TradeService.MAX_LIST_SIZE, latestAll.size() );
        Assert.assertEquals("should evict oldest", 1001,
                latestAll.get(latestAll.size()-1).getId());
        //
        tradeService.addTradeCache(buildTrade(1, 2002));
        latestAll = tradeService.getLatest(1, TradeService.MAX_LIST_SIZE+8);
        Assert.assertEquals("should have right size", TradeService.MAX_LIST_SIZE, latestAll.size() );
        Assert.assertEquals("should evict oldest", 1002,
                latestAll.get(latestAll.size()-1).getId());
    }

    Trade buildTrade(int productIdAndName, int id) {
        Trade t = new Trade();
        t.setProductId(productIdAndName);
        t.setId(id);
        return t;
    }
}
