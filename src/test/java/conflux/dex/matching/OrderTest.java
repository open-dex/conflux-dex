package conflux.dex.matching;

import java.math.BigDecimal;

import org.junit.Assert;
import org.junit.Test;

public class OrderTest {
	@Test
	public void testBuyComparator() {
		Order o1 = Order.limitBuy(3, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Order o2 = Order.limitBuy(2, BigDecimal.valueOf(6), BigDecimal.valueOf(100));
		Order o3 = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		
		Order.BuyComparator comparator = new Order.BuyComparator();
		
		Assert.assertTrue(comparator.compare(o1, o2) > 0);
		Assert.assertTrue(comparator.compare(o3, o2) > 0);
		Assert.assertTrue(comparator.compare(o1, o3) > 0);
	}
	
	@Test
	public void testSellComparator() {
		Order o1 = Order.limitSell(3, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Order o2 = Order.limitSell(2, BigDecimal.valueOf(6), BigDecimal.valueOf(100));
		Order o3 = Order.limitSell(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		
		Order.SellComparator comparator = new Order.SellComparator();
		
		Assert.assertTrue(comparator.compare(o1, o2) < 0);
		Assert.assertTrue(comparator.compare(o3, o2) < 0);
		Assert.assertTrue(comparator.compare(o1, o3) > 0);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testTakeInvalidOrderSide() {
		Order o1 = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Order o2 = Order.limitBuy(2, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		o1.take(o2, 3);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testTakeInvalidMakerOrderType() {
		Order o1 = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Order o2 = Order.marketSell(2, BigDecimal.valueOf(100));
		o1.take(o2, 3);
	}
	
	@Test
	public void testTakePriceMismatch() {
		Order taker = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Order maker = Order.limitSell(2, BigDecimal.valueOf(6), BigDecimal.valueOf(100));
		Assert.assertFalse(taker.take(maker, 3).isPresent());
		
		taker = Order.limitSell(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		maker = Order.limitBuy(2, BigDecimal.valueOf(4), BigDecimal.valueOf(100));
		Assert.assertFalse(taker.take(maker, 3).isPresent());
	}
	
	@Test
	public void testTakeLimitBuy() {
		Order taker = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(20));
		Order maker = Order.limitSell(2, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Assert.assertEquals(BigDecimal.valueOf(20), taker.take(maker, 3).get());
		Assert.assertEquals(BigDecimal.ZERO, taker.getAmount());
		Assert.assertEquals(BigDecimal.valueOf(80), maker.getAmount());
		
		taker = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Assert.assertEquals(BigDecimal.valueOf(80), taker.take(maker, 3).get());
		Assert.assertEquals(BigDecimal.valueOf(20), taker.getAmount());
		Assert.assertEquals(BigDecimal.ZERO, maker.getAmount());
	}
	
	@Test
	public void testTakeLimitSell() {
		Order taker = Order.limitSell(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Order maker = Order.limitBuy(2, BigDecimal.valueOf(5), BigDecimal.valueOf(20));
		Assert.assertEquals(BigDecimal.valueOf(20), taker.take(maker, 3).get());
		Assert.assertEquals(BigDecimal.valueOf(80), taker.getAmount());
		Assert.assertEquals(BigDecimal.ZERO, maker.getAmount());
		
		maker = Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Assert.assertEquals(BigDecimal.valueOf(80), taker.take(maker, 3).get());
		Assert.assertEquals(BigDecimal.ZERO, taker.getAmount());
		Assert.assertEquals(BigDecimal.valueOf(20), maker.getAmount());
	}
	
	@Test
	public void testTakeMarketBuy() {
		Order taker = Order.marketBuy(1, BigDecimal.valueOf(100));
		Order maker = Order.limitSell(2, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
		Assert.assertEquals(0, taker.take(maker, 3).get().compareTo(new BigDecimal("20.000")));
		Assert.assertEquals(0, taker.getAmount().compareTo(new BigDecimal("0.000")));
		Assert.assertEquals(0, maker.getAmount().compareTo(new BigDecimal("80.000")));
	}
	
	@Test
	public void testTakeMarketSell() {
		Order taker = Order.marketSell(1, BigDecimal.valueOf(100));
		Order maker = Order.limitBuy(2, BigDecimal.valueOf(5), BigDecimal.valueOf(20));
		Assert.assertEquals(BigDecimal.valueOf(20), taker.take(maker, 3).get());
		Assert.assertEquals(BigDecimal.valueOf(80), taker.getAmount());
		Assert.assertEquals(BigDecimal.ZERO, maker.getAmount());
	}

	@Test
	public void testGetUnfilled() {
		Order taker = Order.marketBuy(1, BigDecimal.valueOf(100));
		Order maker = Order.limitBuy(2, BigDecimal.valueOf(5), BigDecimal.valueOf(20));
		Assert.assertEquals(0, BigDecimal.valueOf(100).compareTo(taker.getUnfilled()));
		Assert.assertEquals(0, BigDecimal.valueOf(100).compareTo(maker.getUnfilled()));
	}

}
