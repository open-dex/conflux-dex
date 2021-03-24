package conflux.dex.worker;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;

import conflux.dex.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import conflux.dex.common.BusinessFault;
import conflux.dex.common.Utils;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.matching.InstantExchangeLogHandler;
import conflux.dex.matching.Log;
import conflux.dex.matching.LogType;
import conflux.dex.matching.Order;
import conflux.dex.model.Currency;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.Product;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.Trade;
import conflux.dex.worker.ticker.Ticker;

/**
 * Off chain settlement for matched trade. It will update the order status,
 * account balance in database.
 */
public class InstantExchangeTradeSettlement implements InstantExchangeLogHandler {
	private static final Logger logger = LoggerFactory.getLogger(InstantExchangeTradeSettlement.class);
	
	private DexDao dao;
	private Ticker ticker;
	
	public InstantExchangeTradeSettlement(DexDao dao, Ticker ticker) {
		this.dao = dao;
		this.ticker = ticker;
	}

	@Override
	public void onInstantExchangeOrderMatched(InstantExchangeProduct product, Order takerOrder, List<Log> quoteLogs, List<Log> baseLogs) {
		if (quoteLogs == null || baseLogs == null) {
			BigDecimal unfilled = takerOrder.getUnfilled();
			
			this.dao.execute(new TransactionCallbackWithoutResult() {
				
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					dao.updateOrderStatus(takerOrder.getId(), OrderStatus.New, OrderStatus.Cancelled);
					AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getHoldAccountId(), unfilled.negate(), unfilled);
				}
				
			});
			return;
		}
		
		@SuppressWarnings("unchecked")
		LinkedList<Log>[] logs = new LinkedList[2];
		OrderSide[] fakeOrderSides = new OrderSide[2];
		Product[] products = new Product[2];
		// buy 
		logs[0] = (LinkedList<Log>) quoteLogs;
		fakeOrderSides[0] = product.isQuoteIsBaseSide() ? OrderSide.Sell : OrderSide.Buy;
		products[0] = this.dao.getProduct(product.getQuoteProductId()).mustGet();
		logs[1] = (LinkedList<Log>) baseLogs;
		fakeOrderSides[1] = product.isBaseIsBaseSide() ? OrderSide.Buy : OrderSide.Sell;
		products[1] = this.dao.getProduct(product.getBaseProductId()).mustGet();
		// sell
		if (takerOrder.getSide() == OrderSide.Sell) {
			LinkedList<Log> tmp = logs[0];
			logs[0] = logs[1];
			logs[1] = tmp;
			fakeOrderSides[0] = product.isBaseIsBaseSide() ? OrderSide.Sell : OrderSide.Buy;
			fakeOrderSides[1] = product.isQuoteIsBaseSide() ? OrderSide.Buy : OrderSide.Sell;
			Product tmp2 = products[0];
			products[0] = products[1];
			products[1] = tmp2;
		}
		
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				
				// compute traded sell token amount 
				BigDecimal totalTradeAmount = BigDecimal.ZERO;
				for (Log log: logs[0]) {
					if (log.getType() != LogType.OrderMatched)
						continue;
					if (fakeOrderSides[0] == OrderSide.Buy)
						totalTradeAmount = totalTradeAmount.add(Utils.mul(log.getMatchAmount(), log.getMakerOrder().getPrice()));
					else 
						totalTradeAmount = totalTradeAmount.add(log.getMatchAmount());
						
				}
				if (totalTradeAmount.compareTo(takerOrder.getAmount()) > 0)
					throw BusinessFault.OrderInstantExchangeError.rise();
				BigDecimal unfilled = takerOrder.getAmount().subtract(totalTradeAmount);	
				
				// compute traded buy token amount
				BigDecimal totalTradeFunds = BigDecimal.ZERO;
				for (Log log: logs[1]) {
					if (log.getType() != LogType.OrderMatched)
						continue;
					if (fakeOrderSides[1] == OrderSide.Sell)
						totalTradeFunds = totalTradeFunds.add(Utils.mul(log.getMatchAmount(), log.getMakerOrder().getPrice()));
					else 
						totalTradeFunds = totalTradeFunds.add(log.getMatchAmount());
				}
				
				if (takerOrder.getSide() == OrderSide.Buy) {
					BigDecimal tmp = totalTradeAmount;
					totalTradeAmount = totalTradeFunds;
					totalTradeFunds = tmp;
				}
				
				BigDecimal sumTakerFee = BigDecimal.ZERO;
				
				
				for (int k = 0; k < 2; ++k) {
					Currency baseCurrency = dao.getCurrency(products[k].getBaseCurrencyId()).mustGet();
					Currency quoteCurrency = dao.getCurrency(products[k].getQuoteCurrencyId()).mustGet();
					long takerBaseAccountId = AccountService.mustGetAccount(dao, takerOrder.getUserId(), baseCurrency.getName()).getId();
					long takerQuoteAccountId = AccountService.mustGetAccount(dao, takerOrder.getUserId(), quoteCurrency.getName()).getId();
					for (Log log : logs[k]) {
						if (log.getType() == LogType.MakerOrderCompleted) {
							if (!dao.updateOrderStatus(log.getMakerOrder().getId(), OrderStatus.Open, OrderStatus.Filled)) {
								dao.updateOrderStatus(log.getMakerOrder().getId(), OrderStatus.Cancelling, OrderStatus.Filled);
							}
							Events.ORDER_STATUS_CHANGED.fire(log.getMakerOrder());
							continue;
						}
						Order makerOrder = log.getMakerOrder();
						BigDecimal tradeAmount = log.getMatchAmount();
						BigDecimal tradePrice = makerOrder.getPrice();
						BigDecimal tradeFunds = Utils.mul(tradeAmount, tradePrice);
						
						BigDecimal takerFee = BigDecimal.ZERO;
						if (k == 1) {
							takerFee = fakeOrderSides[k] == OrderSide.Buy
									? Utils.mul(BigDecimal.valueOf(takerOrder.getFeeRateTaker()), tradeAmount)
									: Utils.mul(BigDecimal.valueOf(takerOrder.getFeeRateTaker()), tradeFunds);
							sumTakerFee = sumTakerFee.add(takerFee);
						}
						
						BigDecimal makerFee = makerOrder.getSide() == OrderSide.Buy
								? Utils.mul(BigDecimal.valueOf(makerOrder.getFeeRateMaker()), tradeAmount)
								: Utils.mul(BigDecimal.valueOf(makerOrder.getFeeRateMaker()), tradeFunds);
						
						// update account.hold and account.available for taker and maker
						if (fakeOrderSides[k] == OrderSide.Buy) {
							if (k == 1) {
								AccountService.mustUpdateAccountBalance(logger, dao, takerBaseAccountId, BigDecimal.ZERO, tradeAmount.subtract(takerFee));
								AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getFeeAccountId(), BigDecimal.ZERO, takerFee);
							} else {
								AccountService.mustUpdateAccountBalance(logger, dao, takerBaseAccountId, tradeAmount, BigDecimal.ZERO);
							}
							AccountService.mustUpdateAccountBalance(logger, dao, takerQuoteAccountId, tradeFunds.negate(), BigDecimal.ZERO);
							
							AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getBaseAccountId(), tradeAmount.negate(), BigDecimal.ZERO);
							AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getQuoteAccountId(), BigDecimal.ZERO, tradeFunds.subtract(makerFee));
							AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getFeeAccountId(), BigDecimal.ZERO, makerFee);
						} else {
							AccountService.mustUpdateAccountBalance(logger, dao, takerBaseAccountId, tradeAmount.negate(), BigDecimal.ZERO);
							if (k == 1) {
								AccountService.mustUpdateAccountBalance(logger, dao, takerQuoteAccountId, BigDecimal.ZERO, tradeFunds.subtract(takerFee));
								AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getFeeAccountId(), BigDecimal.ZERO, takerFee);
							} else {
								AccountService.mustUpdateAccountBalance(logger, dao, takerQuoteAccountId, tradeFunds, BigDecimal.ZERO);
							}
							
							AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getBaseAccountId(), BigDecimal.ZERO, tradeAmount.subtract(makerFee));
							AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getFeeAccountId(), BigDecimal.ZERO, makerFee);
							AccountService.mustUpdateAccountBalance(logger, dao, makerOrder.getQuoteAccountId(), tradeFunds.negate(), BigDecimal.ZERO);
						}
						
						// update filled amount for maker order
						dao.fillOrder(makerOrder.getId(), tradeAmount, tradeFunds);
						
						Trade trade = new Trade(makerOrder.getProductId(), takerOrder.getId(), makerOrder.getId(), 
								tradePrice, tradeAmount, fakeOrderSides[k], takerFee, makerFee);
					
						dao.addTrade(trade);
						dao.addTradeOrderMap(makerOrder.getId(), trade.getId());
						dao.addTradeUserMap(makerOrder.getUserId(), makerOrder.getProductId(), trade.getCreateTime(), trade.getId());
						
						ticker.update(trade, dao);
						
						Events.ORDER_MATCHED.fire(new TradeDetails(trade, takerOrder, makerOrder));
						Events.INSTANT_EXCHANGE_ORDER_MATCHED.fire(new TradeDetails(trade, takerOrder, makerOrder));
					}
				}
				// update filled amount for taker order
				dao.fillOrder(takerOrder.getId(), totalTradeAmount, totalTradeFunds);
				// update taker balance
				if (unfilled.compareTo(BigDecimal.ZERO) > 0) {
					AccountService.mustUpdateAccountBalance(logger, dao, takerOrder.getHoldAccountId(), unfilled.negate(), unfilled);
				}
				dao.updateOrderStatus(takerOrder.getId(), OrderStatus.New, OrderStatus.Filled);
				
				Trade trade = new Trade(takerOrder.getProductId(), takerOrder.getId(), takerOrder.getId(), 
						Utils.div(totalTradeFunds, totalTradeAmount), totalTradeAmount, takerOrder.getSide(), sumTakerFee, BigDecimal.ZERO);
				dao.addTrade(trade);
				dao.addTradeOrderMap(takerOrder.getId(), trade.getId());
				dao.addTradeUserMap(takerOrder.getUserId(), takerOrder.getProductId(), trade.getCreateTime(), trade.getId());
				Events.INSTANT_EXCHANGE_ORDER_MATCHED.fire(new TradeDetails(trade, takerOrder, takerOrder));
				
				// FIXME fire event to update fee statistics
			}
		});
	}

	@Override
	public void onInstantExchangePendingOrderCancelled(Order order) {
		long orderId = order.getId();
		
		order = this.dao.execute(new TransactionCallback<Order>() {
			
			@Override
			public Order doInTransaction(TransactionStatus status) {
				conflux.dex.model.Order dbOrder = dao.mustGetOrderForUpdate(orderId);
				Order order = Order.place(dbOrder, dao);
				BigDecimal unfilled = order.getUnfilled();
				
				if (!dao.updateOrderStatus(orderId, OrderStatus.Cancelling, OrderStatus.Cancelled)) {
					logger.error("failed to update order status, when = pending order cancelled, orderId = {}, oldStatus = {}, newStatus = {}, currentStatus = {}",
							orderId, OrderStatus.Cancelling, OrderStatus.Cancelled, dbOrder.getStatus());
				}

				AccountService.mustUpdateAccountBalance(logger, dao, order.getHoldAccountId(), unfilled.negate(), unfilled);
				
				if (!order.isEverMatched()) {
					dao.updateCancelOrderRequest(orderId, SettlementStatus.OnChainConfirmed, null, 0);
				}
				
				return order;
			}
			
		});
		
		Events.ORDER_CANCELLED.fire(order);
		Events.ORDER_STATUS_CHANGED.fire(order);
	}

	@Override
	public void onInstantExchangeOrderPended(Order order) {
		if (!this.dao.updateOrderStatus(order.getId(), OrderStatus.New, OrderStatus.Pending)) {
			logger.error("failed to update order status, when = order pended, orderId = {}, oldStatus = {}, newStatus = {}, currentStatus = {}",
					order.getId(), OrderStatus.New, OrderStatus.Pending, this.dao.mustGetOrder(order.getId()).getStatus());
		}	
	}
}
