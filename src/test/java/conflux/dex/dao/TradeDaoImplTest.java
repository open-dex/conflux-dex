package conflux.dex.dao;

import conflux.dex.model.SettlementStatus;
import conflux.dex.model.Trade;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.Instant;

public class TradeDaoImplTest {

    @Test(expected = NullPointerException.class)
    public void testAddTrade() {
        Trade trade = new Trade();
        TradeDaoImpl tradeDao = new TradeDaoImpl();
        tradeDao.addTrade(trade);
    }

    @Test(expected = NullPointerException.class)
    public void testAddTradeOrderMap() {
        TradeDaoImpl tradeDao = new TradeDaoImpl();
        tradeDao.addTradeOrderMap(1L, 1L);
    }

    @Test(expected = NullPointerException.class)
    public void testListTradesByOrderId() {
        TradeDaoImpl tradeDao = new TradeDaoImpl();
        tradeDao.listTradesByOrderId(1L, 1, 1);
    }

    @Test(expected = NullPointerException.class)
    public void testAddTradeUserMap() {
        TradeDaoImpl tradeDao = new TradeDaoImpl();
        tradeDao.addTradeUserMap(1L, 1, Timestamp.from(Instant.now()), 1);
    }

    @Test(expected = NullPointerException.class)
    public void testListRecentTrades() {
        TradeDaoImpl tradeDao = new TradeDaoImpl();
        tradeDao.listRecentTrades(1, 1, 1);
    }

    @Test(expected = NullPointerException.class)
    public void testGetRecentTradeBefore() {
        TradeDaoImpl tradeDao = new TradeDaoImpl();
        tradeDao.getRecentTradeBefore(1, Timestamp.from(Instant.now()));
    }

    @Test(expected = NullPointerException.class)
    public void testUpdateTradeSettlement() {
        TradeDaoImpl tradeDao = new TradeDaoImpl();
        SettlementStatus status = SettlementStatus.OffChainSettled;
        tradeDao.updateTradeSettlement(1L, status, "tx1", 1L);
    }

    @Test(expected = NullPointerException.class)
    public void testListTradesByStatus() {
        TradeDaoImpl tradeDao = new TradeDaoImpl();
        SettlementStatus status = SettlementStatus.OffChainSettled;
        tradeDao.listTradesByStatus(status);
    }

    @Test(expected = NullPointerException.class)
    public void testListTradesByUser() {
        TradeDaoImpl tradeDao = new TradeDaoImpl();
        tradeDao.listTradesByUser(1L, 1, Timestamp.from(Instant.now()), Timestamp.from(Instant.MAX), 1,1, true);
    }
}
