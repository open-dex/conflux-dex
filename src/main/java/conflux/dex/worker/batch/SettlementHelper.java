package conflux.dex.worker.batch;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import conflux.dex.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;

import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.matching.Log;
import conflux.dex.matching.LogHandler;
import conflux.dex.matching.Order;
import conflux.dex.model.CancelOrderReason;
import conflux.dex.model.CancelOrderRequest;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.SettlementStatus;

/**
 * Coordinate with SettlementAggregator to settle order matching logs in batch.
 * 
 * Generally, it handles order matching logs that can not be aggregated. E.g.
 * update order status when order completed.
 * 
 * It also handles any complex cases that not easy to aggregate, e.g. order pended.
 */
class SettlementHelper implements LogHandler {
	
	private static Logger logger = LoggerFactory.getLogger(SettlementHelper.class);
	
	private DexDao dao;
	
	private List<Order> statusChangedOrders = new ArrayList<Order>();
	private List<Order> cancelledOrders = new ArrayList<Order>();
	private List<Order> filledOrders = new ArrayList<Order>();
	
	public SettlementHelper(DexDao dao) {
		this.dao = dao;
	}
	
	private void mustUpdateOrderStatus(String when, long orderId, OrderStatus oldStatus, OrderStatus newStatus) {
		if (!this.dao.updateOrderStatus(orderId, oldStatus, newStatus)) {
			logger.error("failed to update order status, when = {}, orderId = {}, oldStatus = {}, newStatus = {}, currentStatus = {}",
					when, orderId, oldStatus, newStatus, this.dao.mustGetOrder(orderId).getStatus());
		}
	}
	
	private void assertOrderStatus(String when, long orderId, OrderStatus expected) {
		OrderStatus current = this.dao.mustGetOrder(orderId).getStatus();
		if (current != expected) {
			logger.error("unexpected order status, when = {}, expected = {}, current = {}", when, expected, current);
		}
	}

	@Override
	public void onOrderMatched(Order takerOrder, Order makerOrder, BigDecimal tradeAmount) {
		// handled by SettlementAggregator
	}

	@Override
	public void onOrderPended(Order takerOrder, Order makerOrder) {
		if (takerOrder != null) {
			if (this.dao.updateOrderStatus(takerOrder.getId(), OrderStatus.New, OrderStatus.Pending)
					|| this.dao.updateOrderStatus(takerOrder.getId(), OrderStatus.Open, OrderStatus.Pending)) {
				this.statusChangedOrders.add(takerOrder);
			} else {
				assertOrderStatus("taker order pended but failed to change status from New|Open to Pending",
						takerOrder.getId(), OrderStatus.Cancelling);
			}
		}
		
		if (makerOrder != null) {
			if (this.dao.updateOrderStatus(makerOrder.getId(), OrderStatus.Open, OrderStatus.Pending)) {
				this.statusChangedOrders.add(makerOrder);
			} else {
				assertOrderStatus("maker order pended but failed to change status from Open to Pending",
						makerOrder.getId(), OrderStatus.Cancelling);
			}
		}
	}

	@Override
	public void onTakerOrderOpened(Order order) {
		// try to open an order
		if (this.dao.updateOrderStatus(order.getId(), OrderStatus.New, OrderStatus.Open)) {
			this.statusChangedOrders.add(order);
			return;
		}
		
		// when service restarted, opened or canceling orders will be sent to engine
		// to initialize the order books.
		OrderStatus currentStatus = this.dao.mustGetOrder(order.getId()).getStatus();
		if (currentStatus != OrderStatus.Open && currentStatus != OrderStatus.Cancelling) {
			logger.error("unexpected order status, when = taker order opened and need not to change status, expected = Open | Cancelling, current = {}", currentStatus);
		}
	}

	@Override
	public void onMakerOrderCompleted(Order order) {
		if (!this.dao.updateOrderStatus(order.getId(), OrderStatus.Open, OrderStatus.Filled)) {
			this.mustUpdateOrderStatus("maker order completed but failed to change status from Open to Filled",
					order.getId(), OrderStatus.Cancelling, OrderStatus.Filled);
		}
		
		this.dao.addOrderPruneRecord(order.getTimestamp(), order.getId());
		this.filledOrders.add(order);
		this.statusChangedOrders.add(order);
	}

	@Override
	public void onTakerOrderCompleted(Order order) {
		this.mustUpdateOrderStatus("taker order completed", order.getId(), OrderStatus.New, OrderStatus.Filled);
		this.dao.addOrderPruneRecord(order.getTimestamp(), order.getId());
		this.filledOrders.add(order);
		this.statusChangedOrders.add(order);
	}

	@Override
	public void onMakerOrderCancelled(Order order, boolean byAdmin) {
		if (byAdmin && this.dao.updateOrderStatus(order.getId(), OrderStatus.Open, OrderStatus.Cancelled)) {
			CancelOrderRequest request = CancelOrderRequest.fromSystem(order.getId(), CancelOrderReason.AdminRequested);
			if (!order.isEverMatched()) {
				request.setStatus(SettlementStatus.OnChainConfirmed);
			}
			
			this.dao.mustAddCancelOrderRequest(request);
		} else {
			String when = byAdmin ? "maker order cancelled by admin" : "maker order cancelled by customer";
			this.mustUpdateOrderStatus(when, order.getId(), OrderStatus.Cancelling, OrderStatus.Cancelled);
			
			if (!order.isEverMatched() && !this.dao.deleteCancelOrderRequest(order.getId())) {
				logger.error("failed to delete order cancellation reuqest for order id {}", order.getId());
			}
		}
		
		// need to prune order on-chain if ever matched
		if (order.isEverMatched()) {
			this.dao.addOrderPruneRecord(order.getTimestamp(), order.getId());
		}
		
		this.cancelledOrders.add(order);
		this.statusChangedOrders.add(order);
	}

	@Override
	public void onPendingOrderCancelled(Order order) {
		/**
		 * @see BatchTradeSettlement#handle(java.util.List)
		 * @see SettlementHelper#persist(org.springframework.transaction.TransactionStatus, java.util.List)
		 */
		conflux.dex.model.Order dbOrder = this.dao.mustGetOrderForUpdate(order.getId());
		
		/*
		 * Cancel an order that already filled or cancelled.
		 * This is possible when:
		 * 1. Customer cancel an order via REST API.
		 * 2. The order is being settled.
		 */
		if (dbOrder.getStatus().isCompleted()) {
			this.dao.deleteCancelOrderRequest(order.getId());
			return;
		}
		
		if (dbOrder.getStatus() != OrderStatus.Cancelling) {
			logger.error("unexpected order status, when = pending order cancelled, expected = {}, current = {}",
					OrderStatus.Cancelling, dbOrder.getStatus());
			return;
		}
		
		if (order.isCancel()) {
			order = Order.place(dbOrder, this.dao);
		}
		
		BigDecimal unfilled = order.getUnfilled();
		AccountService.mustUpdateAccountBalance(logger, dao, order.getHoldAccountId(), unfilled.negate(), unfilled);
		this.onMakerOrderCancelled(order, false);
	}

	@Override
	public void onTakerOrderCancelled(Order order) {
		this.mustUpdateOrderStatus("taker order cancelled", order.getId(), OrderStatus.New, OrderStatus.Cancelled);
		this.statusChangedOrders.add(order);
		
		// If taker order never matched, just update status in database,
		// and need not to settle on-chain.
		if (!order.isEverMatched()) {
			return;
		}
		
		CancelOrderRequest request = CancelOrderRequest.fromSystem(order.getId(), CancelOrderReason.MarketOrderPartialFilled);
		this.dao.mustAddCancelOrderRequest(request);
		
		this.dao.addOrderPruneRecord(order.getTimestamp(), order.getId());
		this.cancelledOrders.add(order);
	}
	
	public void persist(TransactionStatus status, List<Log> logs) {
		for (Log log : logs) {
			this.handle(log);
		}
	}
	
	public void fires() {
		for (Order order : this.filledOrders) {
			Events.ORDER_FILLED.fire(order);
		}
		
		for (Order order : this.cancelledOrders) {
			Events.ORDER_CANCELLED.fire(order);
		}
		
		for (Order order : this.statusChangedOrders) {
			Events.ORDER_STATUS_CHANGED.fire(order);
		}
	}

}
