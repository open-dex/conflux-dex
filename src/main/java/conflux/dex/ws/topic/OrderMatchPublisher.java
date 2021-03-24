package conflux.dex.ws.topic;

import java.math.BigDecimal;
import java.util.List;

import conflux.dex.dao.DexDao;
import conflux.dex.matching.InstantExchangeLogHandler;
import conflux.dex.matching.Log;
import conflux.dex.matching.LogHandler;
import conflux.dex.matching.Order;
import conflux.dex.model.BalanceChangeType;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.OrderSide;

class OrderMatchPublisher implements LogHandler, InstantExchangeLogHandler {
	
	private AccountTopic topic;
	private DexDao dao;
	private boolean takerOrderMatched;
	
	public OrderMatchPublisher(AccountTopic topic, DexDao dao) {
		this.topic = topic;
		this.dao = dao;
	}

	@Override
	public void onOrderMatched(Order takerOrder, Order makerOrder, BigDecimal tradeAmount) {
		this.takerOrderMatched = true;
		this.publishOnOrderMatched(makerOrder);
	}
	
	@Override
	public void onOrderPended(Order takerOrder, Order makerOrder) {}
	
	private void publishOnOrderMatched(Order order) {
		this.topic.publish(BalanceChangeType.OrderMatch, order.getUserId(), order.getBaseAccountId(), order.getSide() == OrderSide.Buy, true);
		this.topic.publish(BalanceChangeType.OrderMatch, order.getUserId(), order.getQuoteAccountId(), order.getSide() == OrderSide.Sell, true);
	}
	
	private void publicOnOrderCancelled(Order order) {
		if (order.isCancel()) {
			order = Order.place(this.dao.mustGetOrder(order.getId()), this.dao);
		}
		
		this.topic.publish(BalanceChangeType.OrderCancel, order.getUserId(), order.getHoldAccountId(), true, false);
	}

	@Override
	public void onTakerOrderOpened(Order order) {
		if (this.takerOrderMatched) {
			this.publishOnOrderMatched(order);
		}
		
		this.takerOrderMatched = false;
	}

	@Override
	public void onMakerOrderCompleted(Order order) { }

	@Override
	public void onTakerOrderCompleted(Order order) {
		this.publishOnOrderMatched(order);
		this.takerOrderMatched = false;
	}

	@Override
	public void onMakerOrderCancelled(Order order, boolean byAdmin) {
		this.publicOnOrderCancelled(order);
	}
	
	@Override
	public void onPendingOrderCancelled(Order order) {
		this.publicOnOrderCancelled(order);
	}

	@Override
	public void onTakerOrderCancelled(Order order) {
		if (this.takerOrderMatched) {
			this.publishOnOrderMatched(order);
		}
		
		this.takerOrderMatched = false;
	}

	@Override
	public void onInstantExchangeOrderMatched(InstantExchangeProduct product, Order takerOrder, List<Log> quoteLogs,
			List<Log> baseLogs) {
		if (quoteLogs != null) {
			for (Log log : quoteLogs)
				this.handle(log);
		}
		if (baseLogs != null) {
			for (Log log : baseLogs)
				this.handle(log);
		}
		if (this.takerOrderMatched) {
			this.publishOnOrderMatched(takerOrder);
		}
		
		this.takerOrderMatched = false;
	}

	@Override
	public void onInstantExchangePendingOrderCancelled(Order order) {
		this.publicOnOrderCancelled(order);
	}

	@Override
	public void onInstantExchangeOrderPended(Order order) {}

}
