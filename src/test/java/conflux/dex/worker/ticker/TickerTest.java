package conflux.dex.worker.ticker;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;

import conflux.dex.model.OrderSide;
import conflux.dex.model.Tick;
import conflux.dex.model.Trade;
import conflux.dex.service.EngineTester;

public class TickerTest extends EngineTester {
	private Ticker ticker;
	
	@Before
	public void setUp() {
		super.setUp();
		
		this.ticker = new Ticker(this.dao.product.getId(), this.dao.get());
	}
	
	private void addTrade(double price, int amount, String instant) {
		Trade trade = new Trade(this.dao.product.getId(), 2, 1, BigDecimal.valueOf(price), BigDecimal.valueOf(amount), OrderSide.Buy, BigDecimal.ZERO, BigDecimal.ZERO);
		trade.setCreateTime(Timestamp.from(Instant.parse(instant)));
		this.ticker.update(trade);
	}
	
	private Tick mustGetLastTick(TickGranularity granularity) {
		return this.dao.get().mustGetLastTick(this.dao.product.getId(), granularity.getValue());
	}
	
	private void assertTick(Tick tick, TickGranularity granularity, String instant, double open, double high, double low, double close, int baseVolume, double quoteVolume) {
		assertEquals(this.dao.product.getId(), tick.getProductId());
		assertEquals(granularity.getValue(), tick.getGranularity());
		assertEquals(Instant.parse(instant), tick.getCreateTime().toInstant());
		
		assertEquals(open, tick.getOpen().doubleValue(), 0.0001);
		assertEquals(high, tick.getHigh().doubleValue(), 0.0001);
		assertEquals(low, tick.getLow().doubleValue(), 0.0001);
		assertEquals(close, tick.getClose().doubleValue(), 0.0001);
		assertEquals(baseVolume, tick.getBaseCurrencyVolume().intValue());
		assertEquals(0, BigDecimal.valueOf(quoteVolume).compareTo(tick.getQuoteCurrencyVolume()));
	}
	
	@Test
	public void testTick() {
		// 1st minute
		this.addTrade(0.3, 100, "2019-12-10T09:30:05.123Z");
		this.addTrade(0.5, 100, "2019-12-10T09:30:15.123Z");
		this.addTrade(0.2, 100, "2019-12-10T09:30:35.123Z");
		this.addTrade(0.4, 100, "2019-12-10T09:30:55.123Z");
		
		Tick tick = this.mustGetLastTick(DefaultTickGranularity.Minute);
		this.assertTick(tick, DefaultTickGranularity.Minute, "2019-12-10T09:30:00Z", 0.3, 0.5, 0.2, 0.4, 400, 140);
		
		// 2nd minute
		this.addTrade(0.6, 100, "2019-12-10T09:31:01.123Z");
		tick = this.mustGetLastTick(DefaultTickGranularity.Minute);
		this.assertTick(tick, DefaultTickGranularity.Minute, "2019-12-10T09:31:00Z", 0.4, 0.6, 0.6, 0.6, 100, 60);
		
		// check m5
		tick = this.mustGetLastTick(TickGranularity.ofMinutes(5));
		this.assertTick(tick, TickGranularity.ofMinutes(5), "2019-12-10T09:30:00Z", 0.3, 0.6, 0.2, 0.6, 500, 200);
	}
}
