package conflux.dex.matching;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.codahale.metrics.Meter;

import conflux.dex.common.Metrics;
import conflux.dex.common.Utils;
import conflux.dex.dao.DexDao;
import conflux.dex.model.DailyLimitRate;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderType;
import conflux.dex.model.Trade;
import conflux.dex.worker.ticker.DefaultTickGranularity;

/**
 * Order book maintains "Buy" and "Sell" queues for engine to match orders.
 */
public class OrderBook {
	private int productId;
	private int marketBuyAmountScale;
	private EnumMap<OrderSide, Depth> depths = new EnumMap<OrderSide, Depth>(OrderSide.class);
//	private Object lock = new Object();
	private boolean dailyLimit;
	private boolean isOpen;
	private Trade lastClosingTrade;
	private DailyLimitRate dailyLimitRate;

	private static final Meter tpsMatch = Metrics.meter(OrderBook.class, "match");
	
	public OrderBook(int productId, int marketBuyAmountScale) {
		this.productId = productId;
		this.marketBuyAmountScale = marketBuyAmountScale;
		this.lastClosingTrade = null;
		this.dailyLimitRate = null;
		
		for (OrderSide side : OrderSide.values()) {
			this.depths.put(side, new Depth(side));
		}
	}
	/*
	public Object getLock() {
		return this.lock;
	}
	*/
	public void updateDailyLimitInfo(DexDao dao) {
		Optional<DailyLimitRate> maybeDailyLimitRate = dao.getDailyLimitRateByProductId(this.productId);
		if (maybeDailyLimitRate.isPresent()) {
			this.dailyLimitRate = maybeDailyLimitRate.get();
			this.dailyLimit = true;
		} else { 
			this.dailyLimitRate = null;
			this.dailyLimit = false;
		}
		Optional<Trade> result = dao.getRecentTradeBefore(this.productId, Timestamp.from(DefaultTickGranularity.localToday()));
		if (result.isPresent()) {
			this.lastClosingTrade = result.get();
		} else {
			this.lastClosingTrade = null;
		}
	}
	
	private int compareToValidMakerPrice(BigDecimal price) {
		if (this.dailyLimit) {
			BigDecimal lastClosingPrice = this.dailyLimitRate.getInitialPrice();
			if (this.lastClosingTrade != null) {
				lastClosingPrice = this.lastClosingTrade.getPrice();
			} 
			BigDecimal delta = price.subtract(lastClosingPrice);
			BigDecimal rate = BigDecimal.valueOf(this.dailyLimitRate.getUpperLimitRate());
			int ans = 1;
			if (delta.compareTo(BigDecimal.ZERO) <= 0) {
				rate = BigDecimal.valueOf(this.dailyLimitRate.getLowerLimitRate());
				ans = -1;
			}
			if (delta.abs().compareTo(Utils.mul(lastClosingPrice, rate)) > 0)
				return ans;
		}
		return 0;
	}
	
	public List<Log> tryMatch(Order order, boolean revert) {
		List<Log> logs = new LinkedList<Log>();
		List<Order> revertOrders = new LinkedList<Order>();
		
		OrderSide makerSide = order.getSide().opposite();
		Depth makerDepth = this.depths.get(makerSide);

		while (!order.isCompleted()) {
			Optional<Order> maybeMakerOrder = makerDepth.peek();
			if (!maybeMakerOrder.isPresent())
				break;
			
			Order makerOrder;
			if (revert)
				makerOrder = maybeMakerOrder.get().clone();
			else 
				makerOrder = maybeMakerOrder.get();
			
			if (this.compareToValidMakerPrice(makerOrder.getPrice()) != 0) {
				break;
			}
			
			// here order type must be market
			Optional<BigDecimal> tradeAmount = order.take(makerOrder, this.marketBuyAmountScale);
			if (!tradeAmount.isPresent()) {
				break;
			}
			if (tradeAmount.get().compareTo(BigDecimal.ZERO) == 0) {
				break;
			}
			
			// matched
			logs.add(Log.newMatchLog(this.productId, order.clone(), makerOrder, tradeAmount.get()));
			tpsMatch.mark();
			
			if (makerOrder.isCompleted()) {
				if (revert)
					revertOrders.add(maybeMakerOrder.get().clone());
				makerDepth.poll();
				logs.add(Log.newCompleteLog(this.productId, makerOrder, false));
			}
		}
		
		// revert depths 
		if (revert) {
			for (Order revertOrder : revertOrders)
				makerDepth.add(revertOrder);
		}
		return logs;
	}
	
	public List<Log> placeOrder(Order order) {
		List<Log> logs = new LinkedList<Log>();
		
		if (!this.isOpen) {
			// aggregate auction order, set to pending
			logs.add(Log.newPendingLog(this.productId, order, null));
			return logs;
		}

		Depth takerDepth = this.depths.get(order.getSide());
		if (takerDepth.contains(order.getId())) {
			// duplicate order id
			return logs;
		}

		// treat this order as taker, the opposite one as maker.
		OrderSide makerSide = order.getSide().opposite();
		Depth makerDepth = this.depths.get(makerSide);

		// matching
		while (!order.isCompleted()) {
			Optional<Order> maybeMakerOrder = makerDepth.peek();
			if (!maybeMakerOrder.isPresent()) {
				// take order doesn't exist
				break;
			}
			
			Order makerOrder = maybeMakerOrder.get();
			
			if (this.compareToValidMakerPrice(makerOrder.getPrice()) != 0) {
				// out of daily increase/decline limitation
				break;
			}
			
			Optional<BigDecimal> tradeAmount = order.take(makerOrder, this.marketBuyAmountScale);
			if (!tradeAmount.isPresent()) {
				// price mismatch
				break;
			}
			
			if (tradeAmount.get().compareTo(BigDecimal.ZERO) == 0) {
				logs.add(Log.newCancelLog(this.productId, order, true, false));
				return logs;
			}

			BigDecimal funds = Utils.mul(makerOrder.getPrice(), tradeAmount.get());
			Order cloneOrder = buildCloneOrder(order, funds, tradeAmount.get());
			Order cloneMakerOrder = buildCloneOrder(makerOrder, funds, tradeAmount.get());
			logs.add(Log.newMatchLog(this.productId, cloneOrder, cloneMakerOrder, tradeAmount.get()));
			tpsMatch.mark();
			
			if (makerOrder.isCompleted()) {
				makerDepth.poll();
				logs.add(Log.newCompleteLog(this.productId, makerOrder, false));
			}
		}
		
		if (order.isFullyFilled()) {
			logs.add(Log.newCompleteLog(this.productId, order, true));
		} else if (order.getType() == OrderType.Limit) {
			int cmp = this.compareToValidMakerPrice(order.getPrice());
			if ((order.getSide() == OrderSide.Buy && cmp > 0) || (order.getSide() == OrderSide.Sell && cmp < 0)) {
				// set to pending
				logs.add(Log.newPendingLog(this.productId, order, null));
			} else {
				// add to depth
				takerDepth.add(order);
				logs.add(Log.newOpenLog(this.productId, order.clone()));
			}
		} else {
			logs.add(Log.newCancelLog(this.productId, order, true, false));
		}
		return logs;
	}

	private Order buildCloneOrder(Order order, BigDecimal funds, BigDecimal amount) {
		order.setFilledFunds(order.getFilledFunds().add(funds));
		order.setFilledAmount(order.getFilledAmount().add(amount));
		Order ret = order.clone();
		return ret;
	}

	public List<Log> filterOrders() {
		List<Log> logs = new LinkedList<Log>();
		for (OrderSide side : OrderSide.values()) {
			Depth depth = this.depths.get(side);
			while (!depth.isEmpty()) {
				Order order = depth.peek().get();
				int cmp = this.compareToValidMakerPrice(order.getPrice());
				if ((side == OrderSide.Buy && cmp > 0) || (side == OrderSide.Sell && cmp < 0)) {
					depth.poll();
					logs.add(Log.newPendingLog(this.productId, null, order));
				} else {
					break;
				}
			}
		}
		return logs;
	}
	
	public Optional<Order> cancelOrder(long orderId, OrderSide side) {
		return this.depths.get(side).remove(orderId);
	}
	
	public Optional<Order> poll(OrderSide side) {
		return this.depths.get(side).poll();
	}
	
	public List<Order> filter(OrderSide side, Predicate<Order> predicate) {
		return this.depths.get(side).filter(predicate);
	}
	
	public int getProductId() {
		return productId;
	}
	
	public boolean isOpen() {
		return isOpen;
	}

	public void setOpen(boolean isOpen) {
		this.isOpen = isOpen;
	}
	
	public boolean isDailyLimit() {
		return dailyLimit;
	}

	public void setDailyLimit(boolean dailyLimit) {
		this.dailyLimit = dailyLimit;
	}
	
	public Trade getLastClosingTrade() {
		return lastClosingTrade;
	}

	public void setLastClosingTrade(Trade lastClosingTrade) {
		this.lastClosingTrade = lastClosingTrade;
	}
	
	public DailyLimitRate getDailyLimitRate() {
		return dailyLimitRate;
	}

	public void setDailyLimitRate(DailyLimitRate dailyLimitRate) {
		this.dailyLimitRate = dailyLimitRate;
	}
}
