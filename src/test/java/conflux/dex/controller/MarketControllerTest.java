package conflux.dex.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import conflux.dex.model.Tick;
import conflux.dex.service.TickService;
import conflux.dex.worker.ticker.DefaultTickGranularity;

public class MarketControllerTest {

	@Test
	public void testCandleLinesEmpty() {
		List<Tick> lines = TickService.toCandleLines(Collections.emptyList(), 3, DefaultTickGranularity.Minute, Instant.now());
		assertTrue(lines.isEmpty());
	}
	
	private Tick newTestTick(double open, double close, String timestamp) {
		Tick tick = Tick.open(1, 1, BigDecimal.valueOf(open), BigDecimal.ONE, Instant.parse(timestamp));
		tick.setClose(BigDecimal.valueOf(close));
		return tick;
	}
	
	private void assertTick(Tick tick, double open, double close, String timestamp) {
		assertEquals(0, BigDecimal.valueOf(open).compareTo(tick.getOpen()));
		assertEquals(0, BigDecimal.valueOf(close).compareTo(tick.getClose()));
		assertEquals(Instant.parse(timestamp), tick.getCreateTime().toInstant());
	}
	
	@Test
	public void testCandleLines() {
		List<Tick> ticks = Arrays.asList(
				newTestTick(12, 13, "2020-03-18T09:35:00Z"),
				newTestTick(11, 12, "2020-03-18T09:33:00Z"),
				newTestTick(10, 11, "2020-03-18T09:31:00Z"));
		
		// all missed: 0 0 0
		List<Tick> lines = TickService.toCandleLines(ticks, 3, DefaultTickGranularity.Minute, Instant.parse("2020-03-18T09:40:01Z"));
		assertEquals(3, lines.size());
		this.assertTick(lines.get(0), 13, 13, "2020-03-18T09:38:00Z");
		this.assertTick(lines.get(1), 13, 13, "2020-03-18T09:39:00Z");
		this.assertTick(lines.get(2), 13, 13, "2020-03-18T09:40:00Z");
		
		// partial missed: 1 0 0
		lines = TickService.toCandleLines(ticks, 3, DefaultTickGranularity.Minute, Instant.parse("2020-03-18T09:37:01Z"));
		assertEquals(3, lines.size());
		assertEquals(ticks.get(0), lines.get(0));
		this.assertTick(lines.get(1), 13, 13, "2020-03-18T09:36:00Z");
		this.assertTick(lines.get(2), 13, 13, "2020-03-18T09:37:00Z");
		
		// partial missed: 0 1 0
		lines = TickService.toCandleLines(ticks, 3, DefaultTickGranularity.Minute, Instant.parse("2020-03-18T09:36:01Z"));
		assertEquals(3, lines.size());
		this.assertTick(lines.get(0), 12, 12, "2020-03-18T09:34:00Z");
		assertEquals(ticks.get(0), lines.get(1));
		this.assertTick(lines.get(2), 13, 13, "2020-03-18T09:36:00Z");
		
		// partial missed: 1 0 1
		lines = TickService.toCandleLines(ticks, 3, DefaultTickGranularity.Minute, Instant.parse("2020-03-18T09:35:01Z"));
		assertEquals(3, lines.size());
		assertEquals(ticks.get(1), lines.get(0));
		this.assertTick(lines.get(1), 12, 12, "2020-03-18T09:34:00Z");
		assertEquals(ticks.get(0), lines.get(2));
		
		// partial missed: 1 0
		lines = TickService.toCandleLines(Arrays.asList(ticks.get(2)), 3, DefaultTickGranularity.Minute, Instant.parse("2020-03-18T09:32:01Z"));
		assertEquals(2, lines.size());
		assertEquals(ticks.get(2), lines.get(0));
		this.assertTick(lines.get(1), 11, 11, "2020-03-18T09:32:00Z");
		
		// partial missed: 1
		lines = TickService.toCandleLines(Arrays.asList(ticks.get(2)), 3, DefaultTickGranularity.Minute, Instant.parse("2020-03-18T09:31:01Z"));
		assertEquals(1, lines.size());
		assertEquals(ticks.get(2), lines.get(0));	
	}

}
