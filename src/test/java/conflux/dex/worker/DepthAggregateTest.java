package conflux.dex.worker;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;

import conflux.dex.model.OrderSide;
import conflux.dex.worker.DepthAggregate;
import conflux.dex.worker.DepthPriceLevel;

public class DepthAggregateTest {
	
	@Test
	public void testPriceOrderBuySide() {
		DepthAggregate agg = new DepthAggregate(OrderSide.Buy, 6, 0);
		
		agg.add(BigDecimal.valueOf(0.123456), BigDecimal.valueOf(100));
		agg.add(BigDecimal.valueOf(0.123457), BigDecimal.valueOf(100));
		
		List<DepthPriceLevel> levels = agg.getLevels(100);
		assertEquals(2, levels.size());
		assertEquals(new BigDecimal("0.123457"), levels.get(0).getPrice());
		assertEquals(new BigDecimal("0.123456"), levels.get(1).getPrice());
	}
	
	@Test
	public void testPriceOrderSellSide() {
		DepthAggregate agg = new DepthAggregate(OrderSide.Sell, 6, 0);
		
		agg.add(BigDecimal.valueOf(0.123456), BigDecimal.valueOf(100));
		agg.add(BigDecimal.valueOf(0.123457), BigDecimal.valueOf(100));
		
		List<DepthPriceLevel> levels = agg.getLevels(100);
		assertEquals(2, levels.size());
		assertEquals(new BigDecimal("0.123456"), levels.get(0).getPrice());
		assertEquals(new BigDecimal("0.123457"), levels.get(1).getPrice());
	}
	
	@Test
	public void testStep0() {
		DepthAggregate agg = new DepthAggregate(OrderSide.Buy, 6, 0);
		
		agg.add(BigDecimal.valueOf(0.123450), BigDecimal.valueOf(100));
		agg.add(BigDecimal.valueOf(0.123451), BigDecimal.valueOf(100));
		agg.add(BigDecimal.valueOf(0.123460), BigDecimal.valueOf(100));
		
		List<DepthPriceLevel> levels = agg.getLevels(100);
		assertEquals(3, levels.size());
		assertEquals(new BigDecimal("0.123460"), levels.get(0).getPrice());
		assertEquals(new BigDecimal("0.123451"), levels.get(1).getPrice());
		assertEquals(new BigDecimal("0.123450"), levels.get(2).getPrice());
	}
	
	@Test
	public void testStep1() {
		DepthAggregate agg = new DepthAggregate(OrderSide.Buy, 6, 1);
		
		// 0.123450
		agg.add(BigDecimal.valueOf(0.123450), BigDecimal.valueOf(100));
		agg.add(BigDecimal.valueOf(0.123451), BigDecimal.valueOf(100));
		agg.add(BigDecimal.valueOf(0.123455), BigDecimal.valueOf(100));
		agg.add(BigDecimal.valueOf(0.123459), BigDecimal.valueOf(100));
		
		// 0.123460
		agg.add(BigDecimal.valueOf(0.123460), BigDecimal.valueOf(100));
		
		List<DepthPriceLevel> levels = agg.getLevels(100);
		assertEquals(2, levels.size());
		
		assertEquals(new BigDecimal("0.123460"), levels.get(0).getPrice());
		assertEquals(BigDecimal.valueOf(100), levels.get(0).getAmount());
		assertEquals(1, levels.get(0).getCount());
		
		assertEquals(new BigDecimal("0.123450"), levels.get(1).getPrice());
		assertEquals(BigDecimal.valueOf(400), levels.get(1).getAmount());
		assertEquals(4, levels.get(1).getCount());
	}

}
