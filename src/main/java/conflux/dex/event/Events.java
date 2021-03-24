package conflux.dex.event;

import java.util.List;

import conflux.dex.common.Event;
import conflux.dex.matching.Order;
import conflux.dex.model.BestBidOffer;
import conflux.dex.model.Currency;
import conflux.dex.model.Product;
import conflux.dex.model.Tick;
import conflux.dex.model.User;
import conflux.dex.service.blockchain.settle.Settleable;
import conflux.dex.worker.TradeDetails;

public class Events {
	
	public static final Event<User> NEW_USER_ADDED = new Event<User>();
	public static final Event<Currency> NEW_CURRENCY_ADDED = new Event<Currency>();
	public static final Event<Product> NEW_PRODUCT_ADDED = new Event<Product>();
	
	public static final Event<DepositEventArg> DEPOSIT = new Event<DepositEventArg>();
	public static final Event<WithdrawEventArg> WITHDRAW_SUBMITTED = new Event<WithdrawEventArg>();
	public static final Event<WithdrawEventArg> WITHDRAW_COMPLETED = new Event<WithdrawEventArg>();
	public static final Event<WithdrawEventArg> WITHDRAW_FAILED = new Event<WithdrawEventArg>();
	public static final Event<TransferEventArg> TRANSFER = new Event<TransferEventArg>();
	
	public static final Event<OrderEventArg> PLACE_ORDER_SUBMITTED = new Event<OrderEventArg>();
	public static final Event<OrderEventArg> CANCEL_ORDER_SUBMITTED = new Event<OrderEventArg>();
	
	public static final Event<Order> ORDER_STATUS_CHANGED = new Event<Order>();
	public static final Event<TradeDetails> ORDER_MATCHED = new Event<TradeDetails>();
	public static final Event<TradeDetails> INSTANT_EXCHANGE_ORDER_MATCHED = new Event<TradeDetails>();
	public static final Event<Order> ORDER_CANCELLED = new Event<Order>();
	public static final Event<Order> ORDER_FILLED = new Event<Order>();
	public static final Event<List<conflux.dex.model.Order>> PENDING_ORDERS_OPENED = new Event<List<conflux.dex.model.Order>>();
	
	public static final Event<Settleable> TX_DISCARDED = new Event<Settleable>();
	
	public static final Event<BestBidOffer> BBO_CHANGED = new Event<BestBidOffer>();
	public static final Event<Tick> TICK_CHANGED = new Event<Tick>();
	
	public static final Event<String> WORKER_ERROR = new Event<String>();
	public static final Event<String> BLOCKCHAIN_ERROR = new Event<String>();

}
