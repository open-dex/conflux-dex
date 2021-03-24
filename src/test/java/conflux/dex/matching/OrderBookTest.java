package conflux.dex.matching;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import conflux.dex.model.OrderSide;

public class OrderBookTest {
	private OrderBook newTestOrderBook() {
		OrderBook book = new OrderBook(1, 18);
		book.setDailyLimit(false);
		book.setOpen(true);
		return book;
	}
	
	@Test
	public void testTryMatch() {
		OrderBook book = newTestOrderBook();

		// first order
		Order o1 = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		List<Log> logs = book.placeOrder(o1);
		Assert.assertEquals(1, logs.size());
		Assert.assertEquals(LogType.TakerOrderOpened, logs.get(0).getType());

		// fake order
		Order o2 = Order.marketSell(2, BigDecimal.valueOf(5));
		logs = book.tryMatch(o2.clone(), true);
		Assert.assertEquals(1, logs.size());
		for (Log log : logs)
			System.out.println(log);
		logs = book.tryMatch(o2.clone(), false);
		Assert.assertEquals(1, logs.size());
		for (Log log : logs)
			System.out.println(log);
		
		Order o3 = Order.marketBuy(3, BigDecimal.valueOf(50));
		logs = book.tryMatch(o3.clone(), false);
		System.out.println(logs.size());
		
		Order o4 = Order.limitBuy(4, BigDecimal.valueOf(10), BigDecimal.valueOf(50));
		logs = book.placeOrder(o4);
		Assert.assertEquals(1, logs.size());
		Assert.assertEquals(LogType.TakerOrderOpened, logs.get(0).getType());
		
		Order o5 = Order.marketSell(5, BigDecimal.valueOf(130));
		logs = book.tryMatch(o5.clone(), true);
		Assert.assertEquals(3, logs.size());
		for (Log log : logs)
			System.out.println(log);
		System.out.println("!");
		logs = book.tryMatch(o5.clone(), false);
		Assert.assertEquals(3, logs.size());
		for (Log log : logs)
			System.out.println(log);
		System.out.println("#");
		logs = book.tryMatch(o5.clone(), true);
		Assert.assertEquals(2, logs.size());
		for (Log log : logs)
			System.out.println(log);
	}
	
	@Test
	public void testPlaceOrder() {
		OrderBook book = newTestOrderBook();
	
		// first order
		Order o1 = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		List<Log> logs = book.placeOrder(o1);
		Assert.assertEquals(1, logs.size());
		Assert.assertEquals(LogType.TakerOrderOpened, logs.get(0).getType());
		
		// order already exists
		logs = book.placeOrder(o1);
		Assert.assertTrue(logs.isEmpty());
		
		// order price mismatch
		Order o2 = Order.limitSell(2, BigDecimal.valueOf(6), BigDecimal.valueOf(100));
		logs = book.placeOrder(o2);
		Assert.assertEquals(1, logs.size());
		Assert.assertEquals(LogType.TakerOrderOpened, logs.get(0).getType());
		
		// order match
		Order o3 = Order.limitSell(3, BigDecimal.valueOf(5), BigDecimal.valueOf(30));
		logs = book.placeOrder(o3);
		Assert.assertEquals(2, logs.size());
		Assert.assertEquals(LogType.OrderMatched, logs.get(0).getType());
		Assert.assertEquals(LogType.TakerOrderCompleted, logs.get(1).getType());
	}
	
	@Test
	public void testPlaceOrderMultipleMatches() {
		OrderBook book = newTestOrderBook();
		
		// prepare buy orders
		book.placeOrder(Order.limitBuy(1, BigDecimal.valueOf(6), BigDecimal.valueOf(10)));
		book.placeOrder(Order.limitBuy(2, BigDecimal.valueOf(5), BigDecimal.valueOf(100)));
		book.placeOrder(Order.limitBuy(3, BigDecimal.valueOf(4), BigDecimal.valueOf(10)));
		book.placeOrder(Order.limitBuy(4, BigDecimal.valueOf(6), BigDecimal.valueOf(10)));
		
		// order 1: all filled. (1 MatchLog + 1 FilledLog)
		// order 2: partial (30) filled. (1 MatchLog)
		// order 3: price mismatch. (no log)
		// order 4: all filled. (1 MatchLog + 1 FilledLog)
		// order 5: all filled. (1 FilledLog)
		Order o5 = Order.limitSell(5, BigDecimal.valueOf(5), BigDecimal.valueOf(50));
		List<Log> logs = book.placeOrder(o5);
		Assert.assertEquals(6, logs.size());
		
		// order 1 and 4 not exists in book anymore.
		Assert.assertFalse(book.cancelOrder(1, OrderSide.Buy).isPresent());
		Assert.assertFalse(book.cancelOrder(4, OrderSide.Buy).isPresent());
		
		// order 2 could be cancelled partially
		Order o2 = book.cancelOrder(2, OrderSide.Buy).get();
		Assert.assertEquals(BigDecimal.valueOf(5), o2.getPrice());
		Assert.assertEquals(BigDecimal.valueOf(70), o2.getAmount());
		
		// order 3 could be cancelled
		Order o3 = book.cancelOrder(3, OrderSide.Buy).get();
		Assert.assertEquals(BigDecimal.valueOf(4), o3.getPrice());
		Assert.assertEquals(BigDecimal.valueOf(10), o3.getAmount());
	}

	@Test
	public void testFilterOrders() {
		OrderBook book = newTestOrderBook();

		book.placeOrder(Order.limitBuy(1, BigDecimal.valueOf(6), BigDecimal.valueOf(10)));
		book.placeOrder(Order.marketSell(2, BigDecimal.valueOf(10)));
		book.placeOrder(Order.limitBuy(3, BigDecimal.valueOf(4), BigDecimal.valueOf(10)));
		book.placeOrder(Order.limitBuy(4, BigDecimal.valueOf(6), BigDecimal.valueOf(10)));

		List<Log> logs = book.filterOrders();
		Assert.assertTrue(logs.isEmpty());
	}
	
	/*
	 * At the early phase, DEX do not process the trade amount precision for market buy order.
	 * As a result, a market buy (taker) order and a limit sell (maker) order match multiple times.
	 * 
	 * E.g. 
	 * 		- maker order: limit-sell, price = 99.98, amount = 1
	 * 		- taker order: market-buy, price = 0, amount(funds) = 1.04944
	 * 
	 * After the first match, trade funds will be truncated to scale 18, and taker order still has 
	 * 100 drip unfilled. So, the same taker and maker order matches again for the remaining 100 drips.
	 */
	@Test
	public void testMarketBuyPrecisionIssue() {
		OrderBook book = newTestOrderBook();
		
		// limit sell order opened as maker order
		book.placeOrder(Order.limitSell(1, BigDecimal.valueOf(99.98), BigDecimal.ONE));
		
		// taker order
		List<Log> logs = book.placeOrder(Order.marketBuy(2, BigDecimal.valueOf(1.04944)));
		Assert.assertEquals(2, logs.size());
		Assert.assertEquals(LogType.OrderMatched, logs.get(0).getType());
		Assert.assertEquals(LogType.TakerOrderCancelled, logs.get(1).getType());
	}

}
