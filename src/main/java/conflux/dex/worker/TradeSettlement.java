package conflux.dex.worker;

import java.math.BigDecimal;

import conflux.dex.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

import conflux.dex.common.Metrics;
import conflux.dex.common.Utils;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.matching.LogHandler;
import conflux.dex.matching.Order;
import conflux.dex.model.CancelOrderReason;
import conflux.dex.model.CancelOrderRequest;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.OrderType;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.Trade;
import conflux.dex.worker.ticker.Ticker;

/**
 * Off chain settlement for matched trade. It will update the order status,
 * account balance in database.
 */
@Deprecated
public class TradeSettlement implements LogHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(TradeSettlement.class);
	
	private static Timer perfOrderMatched = Metrics.timer(TradeSettlement.class, "perf", "matched");
	
	private DexDao dao;
	private Ticker ticker;
	
	public TradeSettlement(DexDao dao, Ticker ticker) {
		this.dao = dao;
		this.ticker = ticker;
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
		BigDecimal tradePrice = makerOrder.getPrice();
		BigDecimal tradeFunds = Utils.mul(tradeAmount, tradePrice);
		
		BigDecimal takerFee;
		BigDecimal makerFee;
		
		if (takerOrder.getSide() == OrderSide.Buy) {
			takerFee = Utils.mul(BigDecimal.valueOf(takerOrder.getFeeRateTaker()), tradeAmount);
			makerFee = Utils.mul(BigDecimal.valueOf(makerOrder.getFeeRateMaker()), tradeFunds);
		} else {
			takerFee = Utils.mul(BigDecimal.valueOf(takerOrder.getFeeRateTaker()), tradeFunds);
			makerFee = Utils.mul(BigDecimal.valueOf(makerOrder.getFeeRateMaker()), tradeAmount);
		}
		
		Trade trade = new Trade(takerOrder.getProductId(), takerOrder.getId(), makerOrder.getId(), tradePrice, tradeAmount, takerOrder.getSide(), takerFee, makerFee);
		
		try (Context context = perfOrderMatched.time()) {
			this.dao.execute(new TransactionCallbackWithoutResult() {
				
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// update account.hold and account.available for taker, maker and fee owner.
					if (takerOrder.getSide() == OrderSide.Buy) {
						// when maker price is lower than taker price, refund the charge to taker's account
						BigDecimal refundAmount = BigDecimal.ZERO;
						if (takerOrder.getType() == OrderType.Limit) {
							refundAmount = Utils.mul(takerOrder.getPrice(), tradeAmount).subtract(tradeFunds);
						}
						
						AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getBaseAccountId(), BigDecimal.ZERO, tradeAmount.subtract(takerFee));
						AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getFeeAccountId(), BigDecimal.ZERO, takerFee);
						AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getQuoteAccountId(), tradeFunds.add(refundAmount).negate(), refundAmount);

						AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getBaseAccountId(), tradeAmount.negate(), BigDecimal.ZERO);
						AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getQuoteAccountId(), BigDecimal.ZERO, tradeFunds.subtract(makerFee));
						AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getFeeAccountId(), BigDecimal.ZERO, makerFee);
					} else {
						AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getBaseAccountId(), tradeAmount.negate(), BigDecimal.ZERO);
						AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getQuoteAccountId(), BigDecimal.ZERO, tradeFunds.subtract(takerFee));
						AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getFeeAccountId(), BigDecimal.ZERO, takerFee);

						AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getBaseAccountId(), BigDecimal.ZERO, tradeAmount.subtract(makerFee));
						AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getFeeAccountId(), BigDecimal.ZERO, makerFee);
						AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getQuoteAccountId(), tradeFunds.negate(), BigDecimal.ZERO);
					}
					
					// update filled amount and funds for both taker and maker orders
					dao.fillOrder(takerOrder.getId(), tradeAmount, tradeFunds);
					dao.fillOrder(makerOrder.getId(), tradeAmount, tradeFunds);
					
					// add trade
					dao.addTrade(trade);
					dao.addTradeOrderMap(takerOrder.getId(), trade.getId());
					dao.addTradeOrderMap(makerOrder.getId(), trade.getId());
					dao.addTradeUserMap(takerOrder.getUserId(), takerOrder.getProductId(), trade.getCreateTime(), trade.getId());
					if (takerOrder.getUserId() != makerOrder.getUserId()) {
						dao.addTradeUserMap(makerOrder.getUserId(), makerOrder.getProductId(), trade.getCreateTime(), trade.getId());
					}
					
					// update tick
					ticker.update(trade, dao);
				}
				
			});
		}
		
		Events.ORDER_MATCHED.fire(new TradeDetails(trade, takerOrder, makerOrder));
	}
	
	@Override
	public void onOrderPended(Order takerOrder, Order makerOrder) {
		if (takerOrder != null) {
			boolean statusChanged = this.dao.execute(new TransactionCallback<Boolean>() {
				
				@Override
				public Boolean doInTransaction(TransactionStatus status) {
					if (dao.updateOrderStatus(takerOrder.getId(), OrderStatus.New, OrderStatus.Pending)) {
						return true;
					}
					
					if (dao.updateOrderStatus(takerOrder.getId(), OrderStatus.Open, OrderStatus.Pending)) {
						return true;
					}
					
					assertOrderStatus("taker order pended but failed to change status from New|Open to Pending",
							takerOrder.getId(), OrderStatus.Cancelling);
					
					return false;
				}
				
			});
			
			if (statusChanged) {
				Events.ORDER_STATUS_CHANGED.fire(takerOrder);
			}
		}
		
		if (makerOrder != null) {
			boolean statusChanged = this.dao.execute(new TransactionCallback<Boolean>() {

				@Override
				public Boolean doInTransaction(TransactionStatus status) {
					if (dao.updateOrderStatus(makerOrder.getId(), OrderStatus.Open, OrderStatus.Pending)) {
						return true;
					}
					
					assertOrderStatus("maker order pended but failed to change status from Open to Pending",
							takerOrder.getId(), OrderStatus.Cancelling);
					
					return false;
				}
				
			});
			
			if (statusChanged) {
				Events.ORDER_STATUS_CHANGED.fire(makerOrder);
			}
		}
	}

	@Override
	public void onTakerOrderOpened(Order order) {
		boolean statusChanged = this.dao.execute(new TransactionCallback<Boolean>() {

			@Override
			public Boolean doInTransaction(TransactionStatus status) {
				// try to open an order
				if (dao.updateOrderStatus(order.getId(), OrderStatus.New, OrderStatus.Open)) {
					return true;
				}
				
				// when service restarted, opened or canceling orders will be sent to engine
				// to initialize the order books.
				OrderStatus currentStatus = dao.mustGetOrder(order.getId()).getStatus();
				if (currentStatus != OrderStatus.Open && currentStatus != OrderStatus.Cancelling) {
					logger.error("unexpected order status, when = taker order opened and need not to change status, expected = Open | Cancelling, current = {}", currentStatus);
				}
				
				return false;
			}
			
		});
		
		if (statusChanged) {
			Events.ORDER_STATUS_CHANGED.fire(order);
		}
	}

	@Override
	public void onMakerOrderCompleted(Order order) {
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				if (dao.updateOrderStatus(order.getId(), OrderStatus.Open, OrderStatus.Filled)) {
					return;
				}
				
				mustUpdateOrderStatus("maker order completed but failed to change status from Open to Filled",
						order.getId(), OrderStatus.Cancelling, OrderStatus.Filled);
			}
			
		});
		
		Events.ORDER_STATUS_CHANGED.fire(order);
	}

	@Override
	public void onTakerOrderCompleted(Order order) {
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				mustUpdateOrderStatus("taker order completed", order.getId(), OrderStatus.New, OrderStatus.Filled);
			}
			
		});
		
		Events.ORDER_STATUS_CHANGED.fire(order);
	}

	@Override
	public void onMakerOrderCancelled(Order order, boolean byAdmin) {
		BigDecimal unfilled = order.getUnfilled();
		
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				AccountService.mustUpdateAccountBalance(logger, dao, order.getHoldAccountId(), unfilled.negate(), unfilled);
				
				if (byAdmin && dao.updateOrderStatus(order.getId(), OrderStatus.Open, OrderStatus.Cancelled)) {
					CancelOrderRequest request = CancelOrderRequest.fromSystem(order.getId(), CancelOrderReason.AdminRequested);
					if (!order.isEverMatched()) {
						request.setStatus(SettlementStatus.OnChainConfirmed);
					}
					
					dao.mustAddCancelOrderRequest(request);
				} else {
					String when = byAdmin ? "maker order cancelled by admin" : "maker order cancelled by customer";
					mustUpdateOrderStatus(when, order.getId(), OrderStatus.Cancelling, OrderStatus.Cancelled);
					
					if (!order.isEverMatched() && !dao.deleteCancelOrderRequest(order.getId())) {
						logger.error("failed to delete order cancellation reuqest for order id {}", order.getId());
					}
				}
			}
			
		});
		
		Events.ORDER_CANCELLED.fire(order);
		Events.ORDER_STATUS_CHANGED.fire(order);
	}
	
	@Override
	public void onPendingOrderCancelled(Order order) {
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				conflux.dex.model.Order dbOrder = dao.mustGetOrderForUpdate(order.getId());
				
				/*
				 * Cancel an order that already filled or cancelled.
				 * This is possible when:
				 * 1. Customer cancel an order via REST API.
				 * 2. The order is being settled.
				 */
				if (dbOrder.getStatus().isCompleted()) {
					dao.deleteCancelOrderRequest(order.getId());
				} else if (dbOrder.getStatus() == OrderStatus.Cancelling) {
					if (order.isCancel()) {
						onMakerOrderCancelled(Order.place(dbOrder, dao), false);
					} else {
						onMakerOrderCancelled(order, false);
					}
				} else {
					logger.error("unexpected order status, when = pending order cancelled, expected = {}, current = {}",
							OrderStatus.Cancelling, dbOrder.getStatus());
				}
			}
			
		});
	}

	@Override
	public void onTakerOrderCancelled(Order order) {
		BigDecimal unfilled = order.getUnfilled();
		
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				mustUpdateOrderStatus("taker order cancelled", order.getId(), OrderStatus.New, OrderStatus.Cancelled);
				AccountService.mustUpdateAccountBalance(logger, dao, order.getHoldAccountId(), unfilled.negate(), unfilled);
				
				// only add cancellation record when order has ever been matched.
				if (order.isEverMatched()) {
					CancelOrderRequest request = CancelOrderRequest.fromSystem(order.getId(), CancelOrderReason.MarketOrderPartialFilled);
					dao.mustAddCancelOrderRequest(request);
				}
			}
			
		});
		
		if (order.isEverMatched()) {
			Events.ORDER_CANCELLED.fire(order);
		}
		
		Events.ORDER_STATUS_CHANGED.fire(order);
	}

}
