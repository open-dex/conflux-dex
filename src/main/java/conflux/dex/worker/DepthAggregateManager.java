package conflux.dex.worker;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import conflux.dex.common.BusinessFault;
import conflux.dex.event.Events;
import conflux.dex.matching.InstantExchangeLogHandler;
import conflux.dex.matching.Log;
import conflux.dex.matching.LogHandler;
import conflux.dex.matching.Order;
import conflux.dex.model.BestBidOffer;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.OrderSide;

public class DepthAggregateManager implements LogHandler, InstantExchangeLogHandler {
	
	public static final int DefaultSteps[] = {0, 1, 2, 3, 4, 5};
	
	private EnumMap<OrderSide, Map<Integer, DepthAggregate>> aggs;
	
	private int productId;
	private BestBidOffer Bbo; // for step0 only
	private boolean takerOrderMatched;
	
	public DepthAggregateManager(int productId, String product, int priceScale) {
		this.aggs = new EnumMap<OrderSide, Map<Integer,DepthAggregate>>(OrderSide.class);
		
		for (OrderSide side : OrderSide.values()) {
			this.aggs.put(side, new HashMap<Integer, DepthAggregate>());
			
			for (int step : DefaultSteps) {
				this.aggs.get(side).put(step, new DepthAggregate(side, priceScale, step));
			}
		}
		
		this.productId = productId;
		this.Bbo = new BestBidOffer(product);
	}
	
	@Override
	public void onInstantExchangeOrderMatched(InstantExchangeProduct product, Order takerOrder, List<Log> quoteLogs, List<Log> baseLogs) {
		if (quoteLogs == null || baseLogs == null) {
			this.onTakerOrderCancelled(takerOrder);
			return;
		}
		
		List<Log> logs;
		if (this.productId == product.getBaseProductId())
			logs = baseLogs;
		else if (this.productId == product.getQuoteProductId())
			logs = quoteLogs;
		else 
			throw BusinessFault.OrderProductNotMatch.rise();
		for (Log log : logs) {
			this.handle(log);
		}
		if (logs.size() > 0)
			this.onTakerOrderCompleted(takerOrder);
		else 
			this.onTakerOrderCancelled(takerOrder);
	}

	@Override
	public void onOrderMatched(Order takerOrder, Order makerOrder, BigDecimal tradeAmount) {
		for (DepthAggregate agg : this.aggs.get(makerOrder.getSide()).values()) {
			agg.remove(makerOrder.getPrice(), tradeAmount, false);
		}
		
		this.takerOrderMatched = true;
	}
	
	@Override
	public void onOrderPended(Order takerOrder, Order makerOrder) {
		if (makerOrder != null)
			this.onMakerOrderCancelled(makerOrder, false);
	}

	@Override
	public void onTakerOrderOpened(Order order) {
		for (DepthAggregate agg : this.aggs.get(order.getSide()).values()) {
			agg.add(order.getPrice(), order.getAmount());
		}
		
		if (this.takerOrderMatched) {
			this.updateBbo();
			this.takerOrderMatched = false;
		}
	}
	
	private void updateBbo() {
		this.Bbo.setQuoteTime(Instant.now());
		
		// bid
		List<DepthPriceLevel> levels = this.aggs.get(OrderSide.Buy).get(0).getLevels(1);
		if (levels.isEmpty()) {
			this.Bbo.setBid(null);
			this.Bbo.setBidAmount(null);
			this.Bbo.setBidCount(0);
		} else {
			this.Bbo.setBid(levels.get(0).getPrice());
			this.Bbo.setBidAmount(levels.get(0).getAmount());
			this.Bbo.setBidCount(levels.get(0).getCount());
		}
		
		// ask
		levels = this.aggs.get(OrderSide.Sell).get(0).getLevels(1);
		if (levels.isEmpty()) {
			this.Bbo.setAsk(null);
			this.Bbo.setAskAmount(null);
			this.Bbo.setAskCount(0);
		} else {
			this.Bbo.setAsk(levels.get(0).getPrice());
			this.Bbo.setAskAmount(levels.get(0).getAmount());
			this.Bbo.setAskCount(levels.get(0).getCount());
		}
		
		Events.BBO_CHANGED.fire(this.Bbo);
	}

	@Override
	public void onMakerOrderCompleted(Order order) {
		for (DepthAggregate agg : this.aggs.get(order.getSide()).values()) {
			agg.remove(order.getPrice(), BigDecimal.ZERO, true);
		}
	}

	@Override
	public void onTakerOrderCompleted(Order order) {
		this.updateBbo();
		this.takerOrderMatched = false;
	}

	@Override
	public void onMakerOrderCancelled(Order order, boolean byAdmin) {
		for (DepthAggregate agg : this.aggs.get(order.getSide()).values()) {
			agg.remove(order.getPrice(), order.getAmount(), true);
		}
		
		this.updateBbo();
	}
	
	@Override
	public void onPendingOrderCancelled(Order order) {}

	@Override
	public void onTakerOrderCancelled(Order order) {
		if (this.takerOrderMatched) {
			this.updateBbo();
			this.takerOrderMatched = false;
		}
	}
	
	public EnumMap<OrderSide, List<DepthPriceLevel>> getLevels(int step, int depth) {
		EnumMap<OrderSide, List<DepthPriceLevel>> result = new EnumMap<OrderSide, List<DepthPriceLevel>>(OrderSide.class);
		
		for (OrderSide side : OrderSide.values()) {
			DepthAggregate agg = this.aggs.get(side).get(step);
			List<DepthPriceLevel> levels = agg == null ? Collections.emptyList() : agg.getLevels(depth);
			result.put(side, levels);
		}
		
		return result;
	}

	@Override
	public void onInstantExchangePendingOrderCancelled(Order order) {}

	@Override
	public void onInstantExchangeOrderPended(Order order) {}

}
