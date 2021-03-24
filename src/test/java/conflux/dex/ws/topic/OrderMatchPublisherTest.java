package conflux.dex.ws.topic;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import conflux.dex.dao.TestDexDao;
import conflux.dex.matching.Log;
import conflux.dex.matching.Order;
import conflux.dex.matching.OrderBook;

public class OrderMatchPublisherTest {
    private TestDexDao dao;
    private OrderMatchPublisher publisher;
    
    @Before
    public void init() {
    	this.dao = new TestDexDao();
    	this.publisher = new OrderMatchPublisher(new AccountTopic(dao.get()), dao.get());
    }

    @Test
    public void testOnOrderMatched() {
        Order o1 = Order.limitBuy(3, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        o1.setBaseAccountId(dao.aliceCat.getId());
        o1.setQuoteAccountId(dao.aliceDog.getId());
        
        Order o2 = Order.limitSell(3, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        publisher.onOrderMatched(o2, o1, BigDecimal.valueOf(100));
        publisher.onOrderPended(o2, o1);
    }

    @Test
    public void testOnTakerOrder() {
        Order o1 = Order.limitBuy(3, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        o1.setBaseAccountId(dao.aliceCat.getId());
        o1.setQuoteAccountId(dao.aliceDog.getId());
        
        Order o2 = Order.limitSell(3, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        o2.setBaseAccountId(dao.alice.getId());
        o2.setQuoteAccountId(dao.bob.getId());

        publisher.onOrderMatched(o2, o1, BigDecimal.valueOf(100));
        publisher.onTakerOrderOpened(o2);
        publisher.onTakerOrderCompleted(o2);

        publisher.onOrderMatched(o2, o1, BigDecimal.valueOf(100));
        publisher.onTakerOrderCancelled(o2);
    }

    @Test
    public void testOnMakerOrder() {
        Order o1 = Order.limitBuy(3, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        Order o2 = Order.limitSell(3, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        o1.setBaseAccountId(dao.alice.getId());
        o1.setQuoteAccountId(dao.bob.getId());

        publisher.onOrderMatched(o2, o1, BigDecimal.valueOf(100));

        publisher.onMakerOrderCompleted(o1);
        publisher.onPendingOrderCancelled(o1);
        publisher.onMakerOrderCancelled(o1, false);
    }

    @Test
    public void testOnInstantExchange() {
        Order order = Order.marketSell(3, BigDecimal.valueOf(5));
        order.setBaseAccountId(dao.alice.getId());
        order.setQuoteAccountId(dao.bob.getId());

        publisher.onInstantExchangePendingOrderCancelled(order);
        publisher.onInstantExchangeOrderPended(order);
    }

    private OrderBook newTestOrderBook() {
        OrderBook book = new OrderBook(1, 18);
        book.setDailyLimit(false);
        book.setOpen(true);
        return book;
    }

    @Test
    public void testOnInstantExchangeOrderMatched() {
        Order o1 = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        Order o2 = Order.limitSell(2, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        o2.setBaseAccountId(dao.alice.getId());
        o2.setQuoteAccountId(dao.bob.getId());

        OrderBook book = newTestOrderBook();
        List<Log> baseLogs = book.placeOrder(o1);
        List<Log> quoteLogs = book.placeOrder(o2);

        publisher.onInstantExchangeOrderMatched(dao.product3, o2, baseLogs, quoteLogs);
    }



}
