package conflux.dex.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

import conflux.dex.common.Utils;

public class Order {
	
	public static final int MAX_CLIENT_ORDER_ID_LEN = 64;
	
	/**
	 * Order id. (auto-generated)
	 */
	private long id;
	/**
	 * Optional client defined order identity, which is unique for a user.
	 */
	private String clientOrderId;
	/**
	 * Referenced product id.
	 */
	private int productId;
	/**
	 * Referenced user id.
	 */
	private long userId;
	/**
	 * Order creation timestamp.
	 */
	private Timestamp createTime;
	/**
	 * Last update timestamp.
	 */
	private Timestamp updateTime;
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
	 * Address to earn the trade fee.
	 */
	private String feeAddress;
	/**
	 * Trade fee rate for trade as taker (e.g. 0.01 means 1%), precision is 6.
	 */
	private double feeRateTaker;
	/**
	 * Trade fee rate for trade as maker (e.g. 0.01 means 1%), precision is 6.
	 */
	private double feeRateMaker;
	/**
	 * Filled amount of base currency.
	 */
	private BigDecimal filledAmount;
	/**
	 * Filled funds of quote currency.
	 */
	private BigDecimal filledFunds;
	/**
	 * UNIX time in milliseconds.
	 */
	private long timestamp;
	/**
	 * Order hash.
	 */
	private String hash;
	/**
	 * Order signature.
	 */
	private String signature;
	
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
	
	public long getUserId() {
		return userId;
	}
	
	public void setUserId(long userId) {
		this.userId = userId;
	}
	
	public Timestamp getCreateTime() {
		return createTime;
	}
	
	public void setCreateTime(Timestamp createTime) {
		this.createTime = createTime;
	}
	
	public Timestamp getUpdateTime() {
		return updateTime;
	}
	
	public void setUpdateTime(Timestamp updateTime) {
		this.updateTime = updateTime;
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
	
	public String getFeeAddress() {
		return feeAddress;
	}
	
	public void setFeeAddress(String feeAddress) {
		this.feeAddress = feeAddress;
	}
	
	public double getFeeRateTaker() {
		return feeRateTaker;
	}
	
	public void setFeeRateTaker(double feeRateTaker) {
		this.feeRateTaker = feeRateTaker;
	}
	
	public double getFeeRateMaker() {
		return feeRateMaker;
	}
	
	public void setFeeRateMaker(double feeRateMaker) {
		this.feeRateMaker = feeRateMaker;
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
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getHash() {
		return hash;
	}
	
	public void setHash(String hash) {
		this.hash = hash;
	}

	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}
	
	public boolean isMarketBuy() {
		return this.type == OrderType.Market && this.side == OrderSide.Buy;
	}
	
	public boolean isEverMatched() {
		return this.filledAmount.signum() > 0 || this.filledFunds.signum() > 0;
	}
	
	public boolean isFullyFilled() {
		return this.isMarketBuy()
				? this.filledFunds.compareTo(this.amount) == 0
				: this.filledAmount.compareTo(this.amount) == 0;
	}
	
	public BigDecimal getHoldAmount() {
		if (this.side == OrderSide.Sell) {
			return this.amount;
		}
		
		switch (this.type) {
		case Limit:
			return Utils.mul(this.price, this.amount);
		case Market:
			return this.amount;
		default:
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
