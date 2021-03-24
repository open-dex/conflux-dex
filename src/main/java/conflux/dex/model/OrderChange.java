package conflux.dex.model;

import java.math.BigDecimal;

import conflux.dex.common.Utils;

public class OrderChange {
	public static  final String ORDER_MATCHED = "OrderMatched";
	public static  final String ORDER_STATUS_CHANGED = "OrderStatusChanged";
	/**
	 * Changed order ID.
	 */
	private long id;
	/**
	 * Optional client defined order identity, which is unique for a user.
	 */
	private String clientOrderId;
	/**
	 * Product ID of the changed order.
	 */
	private int productId;
	/**
	 * Order type: "Limit", "Market".
	 */
	private OrderType type;
	/**
	 * Order side: "Buy", "Sell".
	 */
	private OrderSide side;
	/**
	 * Order status: "New", "Open", "Cancelling", "Cancelled", "Filled".
	 */
	private OrderStatus status;
	/**
	 * Order price for "Limit" order type only.
	 */
	private BigDecimal price;
	/**
	 * Order amount for "Limit" order type or "Market-Sell" order.
	 * For "Market-Buy" order, it is the total order value.
	 */
	private BigDecimal amount;
	/**
	 * For order matched event, it indicates whether it is a taker order.
	 * For order status changed event, it is true by default.
	 */
	private boolean taker;
	private boolean completed;
	private String eventType;
	/**
	 * Trade price (only exists for order matched event).
	 */
	private BigDecimal tradePrice;
	/**
	 * Trade amount (only exists for order matched event).
	 */
	private BigDecimal tradeAmount;
	/**
	 * Trade fee (only exists for order matched event).
	 * 
	 * For taker order trade, it is calculated by taker fee rate.
	 * For maker order trade, it is calculated by maker fee rate.
	 * 
	 * For "Buy" order trade, it is base currency.
	 * For "Sell" order trade, it is quote currency.
	 */
	private BigDecimal tradeFee;
	/**
	 * Filled amount of base currency.
	 */
	private BigDecimal filledAmount;
	/**
	 * Filled funds of quote currency.
	 */
	private BigDecimal filledFunds;
	
	private static OrderChange from(Order order, boolean taker) {
		OrderChange change = new OrderChange();
		
		change.id = order.getId();
		change.clientOrderId = order.getClientOrderId();
		change.productId = order.getProductId();
		change.type = order.getType();
		change.side = order.getSide();
		change.status = order.getStatus();
		change.price = order.getPrice();
		change.amount = order.getAmount();
		change.taker = taker;
		change.filledAmount = order.getFilledAmount();
		change.filledFunds = order.getFilledFunds();
		
		return change;
	}
	
	public static OrderChange orderStatusChanged(Order order) {
		OrderChange from = from(order, true);
		from.setEventType(ORDER_STATUS_CHANGED);
		return from;
	}
	
	public static OrderChange orderMatched(Trade trade, Order order, boolean taker) {
		OrderChange change = from(order, taker);
		change.setEventType(ORDER_MATCHED);
		change.tradePrice = trade.getPrice();
		change.tradeAmount = trade.getAmount();
		change.tradeFee = taker ? trade.getTakerFee() : trade.getMakerFee();
		
		return change;
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public String getClientOrderId() {
		return clientOrderId;
	}
	
	public void setClientOrderId(String clientOrderId) {
		this.clientOrderId = clientOrderId;
	}
	
	public int getProductId() {
		return productId;
	}
	
	public void setProductId(int productId) {
		this.productId = productId;
	}
	
	public OrderType getType() {
		return type;
	}
	
	public void setType(OrderType type) {
		this.type = type;
	}
	
	public OrderSide getSide() {
		return side;
	}
	
	public void setSide(OrderSide side) {
		this.side = side;
	}
	
	public OrderStatus getStatus() {
		return status;
	}
	
	public void setStatus(OrderStatus status) {
		this.status = status;
	}
	
	public BigDecimal getPrice() {
		return price;
	}
	
	public void setPrice(BigDecimal price) {
		this.price = price;
	}
	
	public BigDecimal getAmount() {
		return amount;
	}
	
	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}
	
	public boolean isTaker() {
		return taker;
	}
	
	public void setTaker(boolean taker) {
		this.taker = taker;
	}
	
	public BigDecimal getTradePrice() {
		return tradePrice;
	}
	
	public void setTradePrice(BigDecimal tradePrice) {
		this.tradePrice = tradePrice;
	}
	
	public BigDecimal getTradeAmount() {
		return tradeAmount;
	}
	
	public void setTradeAmount(BigDecimal tradeAmount) {
		this.tradeAmount = tradeAmount;
	}
	
	public BigDecimal getTradeFee() {
		return tradeFee;
	}
	
	public void setTradeFee(BigDecimal tradeFee) {
		this.tradeFee = tradeFee;
	}
	
	public BigDecimal getFilledAmount() {
		return filledAmount;
	}
	
	public void setFilledAmount(BigDecimal filledAmount) {
		this.filledAmount = filledAmount;
	}
	
	public BigDecimal getFilledFunds() {
		return filledFunds;
	}
	
	public void setFilledFunds(BigDecimal filledFunds) {
		this.filledFunds = filledFunds;
	}

	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
