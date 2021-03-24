package conflux.dex.model;

import java.math.BigDecimal;

import conflux.dex.common.Utils;

public class UserTrade {
	/**
	 * Trade ID.
	 */
	private long id;
	/**
	 * Product ID.
	 */
	private int productId;
	/**
	 * Order ID.
	 */
	private long orderId;
	/**
	 * Order type: "Limit", "Market".
	 */
	private OrderType orderType;
	/**
	 * Order side: "Buy", "Sell".
	 */
	private OrderSide orderSide;
	/**
	 * Trade price from maker order.
	 */
	private BigDecimal tradePrice;
	/**
	 * Trade amount.
	 */
	private BigDecimal tradeAmount;
	/**
	 * Trade fee.
	 * 
	 * For taker order trade, it is calculated by taker fee rate.
	 * For maker order trade, it is calculated by maker fee rate.
	 * 
	 * For "Buy" order trade, it is base currency.
	 * For "Sell" order trade, it is quote currency.
	 */
	private BigDecimal tradeFee;
	/**
	 * Whether the trade is from taker order.
	 */
	private boolean taker;
	/**
	 * Trade time, unix timestamp in milliseconds.
	 */
	private long tradeTime;
	
	public static UserTrade create(Trade trade, OrderType type, boolean taker) {
		UserTrade result = new UserTrade();
		
		result.id = trade.getId();
		result.productId = trade.getProductId();
		result.orderId = taker ? trade.getTakerOrderId() : trade.getMakerOrderId();
		result.orderType = type;
		result.orderSide = taker ? trade.getSide() : trade.getSide().opposite();
		result.tradePrice = trade.getPrice();
		result.tradeAmount = trade.getAmount();
		result.tradeFee = taker ? trade.getTakerFee() : trade.getMakerFee();
		result.taker = taker;
		result.tradeTime = System.currentTimeMillis();
		
		return result;
	}
	
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	
	public int getProductId() {
		return productId;
	}
	
	public void setProductId(int productId) {
		this.productId = productId;
	}
	
	public long getOrderId() {
		return orderId;
	}
	
	public OrderType getOrderType() {
		return orderType;
	}
	
	public void setOrderType(OrderType orderType) {
		this.orderType = orderType;
	}
	
	public OrderSide getOrderSide() {
		return orderSide;
	}
	
	public void setOrderSide(OrderSide orderSide) {
		this.orderSide = orderSide;
	}
	
	public void setOrderId(long orderId) {
		this.orderId = orderId;
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
	
	public boolean isTaker() {
		return taker;
	}
	
	public void setTaker(boolean taker) {
		this.taker = taker;
	}
	
	public long getTradeTime() {
		return tradeTime;
	}
	
	public void setTradeTime(long tradeTime) {
		this.tradeTime = tradeTime;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
