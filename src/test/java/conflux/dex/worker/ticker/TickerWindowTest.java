package conflux.dex.worker.ticker;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.Assert;
import org.junit.Test;

import conflux.dex.model.Tick;
import conflux.dex.model.Trade;

public class TickerWindowTest {
	
	private void move(TickerWindow window, int price, int amount, String tickTime) {
		Tick tick = Tick.open(1, DefaultTickGranularity.Minute.getValue(), BigDecimal.valueOf(price), BigDecimal.valueOf(amount), Instant.parse(tickTime));
		window.move(tick);
	}
	
	private void update(TickerWindow window, int price, int amount, String tradeTime) {
		Trade trade = new Trade();
		trade.setPrice(BigDecimal.valueOf(price));
		trade.setAmount(BigDecimal.valueOf(amount));
		trade.setCreateTime(Timestamp.from(Instant.parse(tradeTime)));
		window.update(trade);
	}
	
	private void assertPrice(Tick tick, int open, int high, int low, int close) {
		Assert.assertEquals(open, tick.getOpen().intValueExact());
		Assert.assertEquals(high, tick.getHigh().intValueExact());
		Assert.assertEquals(low, tick.getLow().intValueExact());
		Assert.assertEquals(close, tick.getClose().intValueExact());
	}
	
	private void assertVolume(Tick tick, int baseCurrencyVolume, int quoteCurrencyVolume, int numTrades) {
		Assert.assertEquals(baseCurrencyVolume, tick.getBaseCurrencyVolume().intValueExact());
		Assert.assertEquals(quoteCurrencyVolume, tick.getQuoteCurrencyVolume().intValueExact());
		Assert.assertEquals(numTrades, tick.getCount());
	}
	
	private void assertTime(Tick tick, String createTime, String updateTime) {
		Assert.assertEquals(Instant.parse(createTime), tick.getCreateTime().toInstant());
		Assert.assertEquals(Instant.parse(updateTime), tick.getUpdateTime().toInstant());
	}
	
	@Test
	public void testUpdate() {
		TickerWindow window = new TickerWindow();
		
		this.move(window, 10, 100, "2020-05-17T09:30:00Z");
		
		// update with higher price
		this.update(window, 15, 10, "2020-05-17T09:30:01Z");
		Tick agg = window.getAggregate(Instant.parse("2020-05-17T09:30:02Z"));
		this.assertPrice(agg, 10, 15, 10, 15);
		this.assertVolume(agg, 110, 1150, 2);
		Assert.assertEquals("2020-05-17T09:30:01Z", agg.getUpdateTime().toInstant().toString());
		
		// update with lower price
		this.update(window, 5, 5, "2020-05-17T09:30:03Z");
		agg = window.getAggregate(Instant.parse("2020-05-17T09:30:04Z"));
		this.assertPrice(agg, 10, 15, 5, 5);
		this.assertVolume(agg, 115, 1175, 3);
		Assert.assertEquals("2020-05-17T09:30:03Z", agg.getUpdateTime().toInstant().toString());
	}
	
	@Test
	public void testMoveContinous() {
		TickerWindow window = new TickerWindow(Duration.ofMinutes(3));
		
		this.move(window, 5, 100, "2020-05-17T09:30:00Z");
		Tick agg = window.getAggregate(Instant.parse("2020-05-17T09:30:01Z"));
		this.assertPrice(agg, 5, 5, 5, 5);
		this.assertVolume(agg, 100, 500, 1);
		
		this.move(window, 8, 100, "2020-05-17T09:31:00Z");
		agg = window.getAggregate(Instant.parse("2020-05-17T09:31:01Z"));
		this.assertPrice(agg, 5, 8, 5, 8);
		this.assertVolume(agg, 200, 1300, 2);
		
		this.move(window, 2, 100, "2020-05-17T09:32:00Z");
		agg = window.getAggregate(Instant.parse("2020-05-17T09:32:01Z"));
		this.assertPrice(agg, 5, 8, 2, 2);
		this.assertVolume(agg, 300, 1500, 3);
		
		this.move(window, 6, 100, "2020-05-17T09:33:00Z");
		agg = window.getAggregate(Instant.parse("2020-05-17T09:33:01Z"));
		this.assertPrice(agg, 5, 8, 2, 6);
		this.assertVolume(agg, 400, 2100, 4);
		
		this.move(window, 3, 100, "2020-05-17T09:34:00Z");
		agg = window.getAggregate(Instant.parse("2020-05-17T09:34:01Z"));
		this.assertPrice(agg, 8, 8, 2, 3);
		this.assertVolume(agg, 400, 1900, 4);
	}
	
	@Test
	public void testMoveNotContinous() {
		TickerWindow window = new TickerWindow(Duration.ofMinutes(3));
		
		// tick 0
		this.move(window, 2, 100, "2020-05-17T09:29:00Z");
		Tick agg = window.getAggregate(Instant.parse("2020-05-17T09:29:01Z"));
		this.assertPrice(agg, 2, 2, 2, 2);
		this.assertVolume(agg, 100, 200, 1);
		
		// tick 1: no tick out of date
		this.move(window, 5, 100, "2020-05-17T09:30:00Z");
		agg = window.getAggregate(Instant.parse("2020-05-17T09:30:01Z"));
		this.assertPrice(agg, 2, 5, 2, 5);
		this.assertVolume(agg, 200, 700, 2);
		
		// tick 2: lead to tick 0 & 1 removed
		this.move(window, 3, 100, "2020-05-17T09:34:00Z");
		agg = window.getAggregate(Instant.parse("2020-05-17T09:34:01Z"));
		this.assertPrice(agg, 5, 5, 3, 3);
		this.assertVolume(agg, 100, 300, 1);
		
		// tick 3: lead to tick 2 as start point of time window
		this.move(window, 8, 100, "2020-05-17T09:37:00Z");
		agg = window.getAggregate(Instant.parse("2020-05-17T09:37:01Z"));
		this.assertPrice(agg, 3, 8, 3, 8);
		this.assertVolume(agg, 200, 1100, 2);
		
		// tick 4: lead to tick 3 as middle point of time window
		this.move(window, 4, 100, "2020-05-17T09:38:00Z");
		agg = window.getAggregate(Instant.parse("2020-05-17T09:38:01Z"));
		this.assertPrice(agg, 3, 8, 3, 4);
		this.assertVolume(agg, 200, 1200, 2);
	}
	
	@Test
	public void testGet() {
		TickerWindow window = new TickerWindow(Duration.ofMinutes(3));
		this.move(window, 5, 100, "2020-05-17T09:30:00Z");
		this.move(window, 6, 100, "2020-05-17T09:32:00Z");
		
		// get with the last tick time
		Tick agg = window.getAggregate(Instant.parse("2020-05-17T09:32:01Z"));
		this.assertPrice(agg, 5, 6, 5, 6);
		this.assertVolume(agg, 200, 1100, 2);
		this.assertTime(agg, "2020-05-17T09:30:00Z", "2020-05-17T09:32:00Z");
		
		// get with the last tick time + 1 min
		agg = window.getAggregate(Instant.parse("2020-05-17T09:33:01Z"));
		this.assertPrice(agg, 5, 6, 5, 6);
		this.assertVolume(agg, 200, 1100, 2);
		this.assertTime(agg, "2020-05-17T09:30:00Z", "2020-05-17T09:33:00Z");
		
		// get with the last tick time + 2 min
		// and first tick out of date
		agg = window.getAggregate(Instant.parse("2020-05-17T09:34:01Z"));
		this.assertPrice(agg, 5, 6, 5, 6);
		this.assertVolume(agg, 100, 600, 1);
		this.assertTime(agg, "2020-05-17T09:31:00Z", "2020-05-17T09:34:00Z");
		
		// get with the last tick time + 3 min
		agg = window.getAggregate(Instant.parse("2020-05-17T09:35:01Z"));
		this.assertPrice(agg, 6, 6, 6, 6);
		this.assertVolume(agg, 100, 600, 1);
		this.assertTime(agg, "2020-05-17T09:32:00Z", "2020-05-17T09:35:00Z");
		
		// get with the last tick time + 4 min
		// all ticks are out of date
		agg = window.getAggregate(Instant.parse("2020-05-17T09:36:01Z"));
		this.assertPrice(agg, 6, 6, 6, 6);
		this.assertVolume(agg, 0, 0, 0);
		this.assertTime(agg, "2020-05-17T09:33:00Z", "2020-05-17T09:36:00Z");
	}
	
	@Test
	public void testGetAndMoveAfterLongTime() {
		TickerWindow window = new TickerWindow(Duration.ofMinutes(3));
		
		// initialize with the latest trade that long time ago
		this.move(window, 5, 100, "2020-05-20T09:30:00Z");
		
		// Get aggregate tick and move time window: out of dated tick removed
		Tick agg = window.getAggregate(Instant.parse("2020-05-21T09:30:01Z"));
		this.assertPrice(agg, 5, 5, 5, 5);
		this.assertVolume(agg, 0, 0, 0);
		this.assertTime(agg, "2020-05-21T09:27:00Z", "2020-05-21T09:30:00Z");
		
		// Get aggregate tick and move time window: only start/end time changed.
		agg = window.getAggregate(Instant.parse("2020-05-21T09:31:01Z"));
		this.assertPrice(agg, 5, 5, 5, 5);
		this.assertVolume(agg, 0, 0, 0);
		this.assertTime(agg, "2020-05-21T09:28:00Z", "2020-05-21T09:31:00Z");
		
		// New tick added and time window moved forward.
		this.move(window, 1, 10, "2020-05-23T09:30:00Z");
		agg = window.getAggregate(Instant.parse("2020-05-23T09:30:01Z"));
		this.assertPrice(agg, 5, 5, 1, 1);
		this.assertVolume(agg, 10, 10, 1);
		this.assertTime(agg, "2020-05-23T09:27:00Z", "2020-05-23T09:30:00Z");
	}
	
	@Test
	public void testPriceQueueMax() {
		PriceQueue queue = new PriceQueue(true);
		
		// empty queue
		Assert.assertFalse(queue.getFirst().isPresent());
		Assert.assertFalse(queue.getLast().isPresent());
		Assert.assertFalse(queue.dequeue(BigDecimal.valueOf(5)));
		
		// insert price 5
		Assert.assertEquals(BigDecimal.valueOf(5), queue.enqueue(BigDecimal.valueOf(5)));
		Assert.assertEquals(5, queue.getFirst().get().intValueExact());
		Assert.assertEquals(5, queue.getLast().get().intValueExact());
		
		// insert price 3: [5, 3]
		Assert.assertEquals(BigDecimal.valueOf(5), queue.enqueue(BigDecimal.valueOf(3)));
		Assert.assertEquals(5, queue.getFirst().get().intValueExact());
		Assert.assertEquals(3, queue.getLast().get().intValueExact());
		
		// insert price 5: [5(2)]
		Assert.assertEquals(BigDecimal.valueOf(5), queue.enqueue(BigDecimal.valueOf(5)));
		Assert.assertEquals(5, queue.getFirst().get().intValueExact());
		Assert.assertEquals(5, queue.getLast().get().intValueExact());
		
		// remove price 5: [5(1)]
		Assert.assertTrue(queue.dequeue(BigDecimal.valueOf(5)));
		Assert.assertEquals(5, queue.getFirst().get().intValueExact());
		Assert.assertEquals(5, queue.getLast().get().intValueExact());
		
		// remove price 5: []
		Assert.assertTrue(queue.dequeue(BigDecimal.valueOf(5)));
		Assert.assertFalse(queue.getFirst().isPresent());
		Assert.assertFalse(queue.getLast().isPresent());
	}
	
	@Test
	public void testPriceQueueMin() {
		PriceQueue queue = new PriceQueue(false);
		
		// insert price 5: [5]
		Assert.assertEquals(BigDecimal.valueOf(5), queue.enqueue(BigDecimal.valueOf(5)));
		Assert.assertEquals(5, queue.getFirst().get().intValueExact());
		Assert.assertEquals(5, queue.getLast().get().intValueExact());
		
		// insert price 6: [5, 6]
		Assert.assertEquals(BigDecimal.valueOf(5), queue.enqueue(BigDecimal.valueOf(6)));
		Assert.assertEquals(5, queue.getFirst().get().intValueExact());
		Assert.assertEquals(6, queue.getLast().get().intValueExact());
		
		// insert price 3: [3]
		Assert.assertEquals(BigDecimal.valueOf(3), queue.enqueue(BigDecimal.valueOf(3)));
		Assert.assertEquals(3, queue.getFirst().get().intValueExact());
		Assert.assertEquals(3, queue.getLast().get().intValueExact());
		
		// cannot remove prices that not equals to the first one
		Assert.assertFalse(queue.dequeue(BigDecimal.valueOf(2)));
		Assert.assertFalse(queue.dequeue(BigDecimal.valueOf(4)));
		Assert.assertTrue(queue.dequeue(BigDecimal.valueOf(3)));
	}

}
