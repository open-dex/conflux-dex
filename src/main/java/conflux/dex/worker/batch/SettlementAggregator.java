package conflux.dex.worker.batch;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import conflux.dex.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;

import conflux.dex.common.Utils;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.matching.Log;
import conflux.dex.matching.LogHandler;
import conflux.dex.matching.Order;
import conflux.dex.model.FeeData;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderType;
import conflux.dex.model.Tick;
import conflux.dex.model.Trade;
import conflux.dex.service.FeeService;
import conflux.dex.worker.TradeDetails;
import conflux.dex.worker.ticker.Ticker;

/**
 * Aggregates operations of different order match logs to settle in a batch, including:
 * 
 * 1. Aggregates balance changes of same user.
 * 2. Aggregates filled amounts/funds among trades of same order.
 * 3. Aggregates tick changes of all trades to settle only once.
 */
class SettlementAggregator implements LogHandler {
	
	private static Logger logger = LoggerFactory.getLogger(SettlementAggregator.class);
	
	// aggregated balance changes
	private Map<Long, BigDecimal> balanceHoldChanges = new HashMap<Long, BigDecimal>();			// map<accountId, holdDelta>
	private Map<Long, BigDecimal> balanceAvailableChanges = new HashMap<Long, BigDecimal>();	// map<accountId, availableDelta>
	
	// aggregated order fills
	private Map<Long, BigDecimal> orderFilledAmountChanges = new HashMap<Long, BigDecimal>();	// map<orderId, filledAmount>
	private Map<Long, BigDecimal> orderFilledFundsChanges = new HashMap<Long, BigDecimal>();	// map<orderId, filledFunds>
	
	// generated trades for each OrderMatched log
	private List<TradeDetails> trades = new LinkedList<TradeDetails>();
	
	// aggregated tick for multiple trades to update in database only once in a batch operation.
	private Tick aggregatedTick;
	
	// unified timestamp for all trades and aggregated tick
	private Timestamp ts = Timestamp.from(Instant.now());
	private FeeService feeService;

	public static SettlementAggregator aggregate(List<Log> logs, FeeService feeService) {
		SettlementAggregator aggregator = new SettlementAggregator();
		aggregator.feeService = feeService;
		for (Log log : logs) {
			aggregator.handle(log);
		}

		return aggregator;
	}
	
	private void updateBalanceChanges(long accountId, BigDecimal holdDelta, BigDecimal availableDelta) {
		if (accountId < 0) {
			return;
		}
		
		BigDecimal hold = this.balanceHoldChanges.getOrDefault(accountId, BigDecimal.ZERO);
		this.balanceHoldChanges.put(accountId, hold.add(holdDelta));
		
		BigDecimal available = this.balanceAvailableChanges.getOrDefault(accountId, BigDecimal.ZERO);
		this.balanceAvailableChanges.put(accountId, available.add(availableDelta));
	}
	
	private void updateOrderFilledChanges(long orderId, BigDecimal amount, BigDecimal funds) {
		BigDecimal filledAmount = this.orderFilledAmountChanges.getOrDefault(orderId, BigDecimal.ZERO);
		this.orderFilledAmountChanges.put(orderId, filledAmount.add(amount));
		
		BigDecimal filledFunds = this.orderFilledFundsChanges.getOrDefault(orderId, BigDecimal.ZERO);
		this.orderFilledFundsChanges.put(orderId, filledFunds.add(funds));
	}

	@Override
	public void onOrderMatched(Order takerOrder, Order makerOrder, BigDecimal tradeAmount) {
		BigDecimal tradePrice = makerOrder.getPrice();
		BigDecimal tradeFunds = Utils.mul(tradeAmount, tradePrice);
		
		BigDecimal feeFromTaker;	// total trade fee from taker
		BigDecimal feeFromMaker;	// total trade fee from maker
		FeeData feeData = feeService.getData();
		if (takerOrder.getSide() == OrderSide.Buy) {
			feeFromTaker = Utils.mul(BigDecimal.valueOf(takerOrder.getFeeRateTaker()), tradeAmount);
			feeFromMaker = Utils.mul(BigDecimal.valueOf(makerOrder.getFeeRateMaker()), tradeFunds);
		} else {
			feeFromTaker = Utils.mul(BigDecimal.valueOf(takerOrder.getFeeRateTaker()), tradeFunds);
			feeFromMaker = Utils.mul(BigDecimal.valueOf(makerOrder.getFeeRateMaker()), tradeAmount);
		}
		
		// partial taker fee to DEX platform
		BigDecimal takerFeeToDex = feeData.getTakerFee(feeFromTaker);
		// partial maker fee to DEX platform
		BigDecimal makerFeeToDex = feeData.getMakerFee(feeFromMaker);
		// partial taker fee to order creator
		BigDecimal takerFeeToCreator = feeFromTaker.subtract(takerFeeToDex);
		// partial maker fee to order creator
		BigDecimal makerFeeToCreator = feeFromMaker.subtract(makerFeeToDex);
		
		Trade trade = new Trade(takerOrder.getProductId(), takerOrder.getId(), makerOrder.getId(), 
				tradePrice, tradeAmount, takerOrder.getSide(), feeFromTaker, feeFromMaker);
		trade.setCreateTime(this.ts);
		trade.setUpdateTime(this.ts);
		
		// calculate base/quote account id of DEX fee recipient to update balance
		long dexFeeRecipientBaseAccountId = -1;
		long dexFeeRecipientQuoteAccountId = -1;
		switch (feeData.getStrategy()) {
		case FeeToDex:
			long[] dexAccountIds = feeService.getDexFeeRecipientAccountIds(trade.getProductId());
			dexFeeRecipientBaseAccountId = dexAccountIds[0];
			dexFeeRecipientQuoteAccountId = dexAccountIds[1];
			break;
		case FeeToMaker:
			dexFeeRecipientBaseAccountId = makerOrder.getBaseAccountId();
			dexFeeRecipientQuoteAccountId = makerOrder.getQuoteAccountId();
			break;
		default:
			break;
		}
		
		// update account.hold and account.available for taker, maker and fee owner.
		if (takerOrder.getSide() == OrderSide.Buy) {
			// when maker price is lower than taker price, refund the charge to taker's account
			BigDecimal refundAmount = BigDecimal.ZERO;
			if (takerOrder.getType() == OrderType.Limit) {
				refundAmount = Utils.mul(takerOrder.getPrice(), tradeAmount).subtract(tradeFunds);
			}
			
			// update account balances for taker order
			this.updateBalanceChanges(takerOrder.getBaseAccountId(), BigDecimal.ZERO, tradeAmount.subtract(feeFromTaker));
			this.updateBalanceChanges(takerOrder.getFeeAccountId(), BigDecimal.ZERO, takerFeeToCreator);
			this.updateBalanceChanges(dexFeeRecipientBaseAccountId, BigDecimal.ZERO, takerFeeToDex);
			this.updateBalanceChanges(takerOrder.getQuoteAccountId(), tradeFunds.add(refundAmount).negate(), refundAmount);
			
			// update account balances for maker order
			this.updateBalanceChanges(makerOrder.getBaseAccountId(), tradeAmount.negate(), BigDecimal.ZERO);
			this.updateBalanceChanges(makerOrder.getQuoteAccountId(), BigDecimal.ZERO, tradeFunds.subtract(feeFromMaker));
			this.updateBalanceChanges(makerOrder.getFeeAccountId(), BigDecimal.ZERO, makerFeeToCreator);
			this.updateBalanceChanges(dexFeeRecipientQuoteAccountId, BigDecimal.ZERO, makerFeeToDex);
		} else {
			// update account balances for taker order
			this.updateBalanceChanges(takerOrder.getBaseAccountId(), tradeAmount.negate(), BigDecimal.ZERO);
			this.updateBalanceChanges(takerOrder.getQuoteAccountId(), BigDecimal.ZERO, tradeFunds.subtract(feeFromTaker));
			this.updateBalanceChanges(takerOrder.getFeeAccountId(), BigDecimal.ZERO, takerFeeToCreator);
			this.updateBalanceChanges(dexFeeRecipientQuoteAccountId, BigDecimal.ZERO, takerFeeToDex);
			
			// update account balances for maker order
			this.updateBalanceChanges(makerOrder.getBaseAccountId(), BigDecimal.ZERO, tradeAmount.subtract(feeFromMaker));
			this.updateBalanceChanges(makerOrder.getFeeAccountId(), BigDecimal.ZERO, makerFeeToCreator);
			this.updateBalanceChanges(dexFeeRecipientBaseAccountId, BigDecimal.ZERO, makerFeeToDex);
			this.updateBalanceChanges(makerOrder.getQuoteAccountId(), tradeFunds.negate(), BigDecimal.ZERO);
		}
		
		// update filled amount and funds for both taker and maker orders
		this.updateOrderFilledChanges(takerOrder.getId(), tradeAmount, tradeFunds);
		this.updateOrderFilledChanges(makerOrder.getId(), tradeAmount, tradeFunds);
		
		// add trade
		this.trades.add(new TradeDetails(trade, takerOrder, makerOrder));
		
		// update tick
		if (this.aggregatedTick == null) {
			this.aggregatedTick = Tick.open(trade.getProductId(), 0, trade.getPrice(), trade.getAmount(), this.ts.toInstant());
		} else {
			this.aggregatedTick.update(trade);
		}
	}

	@Override
	public void onOrderPended(Order takerOrder, Order makerOrder) {
		// do nothing
	}

	@Override
	public void onTakerOrderOpened(Order order) {
		// do nothing
	}

	@Override
	public void onMakerOrderCompleted(Order order) {
		// do nothing
	}

	@Override
	public void onTakerOrderCompleted(Order order) {
		// do nothing
	}

	@Override
	public void onMakerOrderCancelled(Order order, boolean byAdmin) {
		BigDecimal unfilled = order.getUnfilled();
		this.updateBalanceChanges(order.getHoldAccountId(), unfilled.negate(), unfilled);
	}

	@Override
	public void onPendingOrderCancelled(Order order) {
		// do nothing
	}

	@Override
	public void onTakerOrderCancelled(Order order) {
		BigDecimal unfilled = order.getUnfilled();
		this.updateBalanceChanges(order.getHoldAccountId(), unfilled.negate(), unfilled);
	}
	
	public void persist(TransactionStatus status, DexDao dao, Ticker ticker) {
		// update filled amount and funds for both taker and maker orders
		for (Map.Entry<Long, BigDecimal> entry : this.orderFilledAmountChanges.entrySet()) {
			BigDecimal filledFunds = this.orderFilledFundsChanges.get(entry.getKey());
			dao.fillOrder(entry.getKey(), entry.getValue(), filledFunds);
		}
		
		// Add trade after order update to avoid deadlock, because trade
		// use order id as foreign key, which acquires shared read lock.
		for (TradeDetails details : this.trades) {
			Trade trade = details.getTrade();
			dao.addTrade(trade);
			
			Order takerOrder = details.getTakerOrder();
			Order makerOrder = details.getMakerOrder();
			dao.addTradeOrderMap(takerOrder.getId(), trade.getId());
			dao.addTradeOrderMap(makerOrder.getId(), trade.getId());
			
			dao.addTradeUserMap(takerOrder.getUserId(), trade.getProductId(), trade.getCreateTime(), trade.getId());
			if (takerOrder.getUserId() != makerOrder.getUserId()) {
				dao.addTradeUserMap(makerOrder.getUserId(), trade.getProductId(), trade.getCreateTime(), trade.getId());
			}
		}
		
		// update tick
		if (this.aggregatedTick != null) {
			ticker.update(this.aggregatedTick, dao);
		}
		
		// Update account.hold and account.available for taker, maker and fee owner.
		// Note, update balances at last to reduce the database write lock time among different products.
		// E.g. both BTC-USDT and ETH-USDT will update the same USDT account.
		for (Map.Entry<Long, BigDecimal> entry : this.balanceHoldChanges.entrySet()) {
			AccountService.mustUpdateAccountBalance(logger, dao, entry.getKey(), entry.getValue(), this.balanceAvailableChanges.get(entry.getKey()));
		}
	}
	
	public void fires() {
		for (TradeDetails details : this.trades) {
			Events.ORDER_MATCHED.fire(details);
		}
	}

}
