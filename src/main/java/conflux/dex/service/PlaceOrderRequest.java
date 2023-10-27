package conflux.dex.service;

import java.math.BigDecimal;

import javax.validation.constraints.Max;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Utils;
import conflux.dex.common.Validators;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.OrderType;
import conflux.dex.model.Product;
import conflux.web3j.types.AddressType;

public class PlaceOrderRequest {
	
	public static final double FEE_RATE_TAKER_MIN = 0;
	
	/**
	 * Optional client defined order identity, which is unique for a user.
	 */
	@Max(64)
	private String clientOrderId;
	/**
	 * User address.
	 */
	private String address;
	/**
	 * Product name.
	 */
	private String product;
	/**
	 * Order type: "Limit", "Market".
	 */
	private OrderType type;
	/**
	 * Order side: "Buy", "Sell".
	 */
	private OrderSide side;
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
	 * UNIX time in milliseconds.
	 */
	private long timestamp;
	/**
	 * Order signature.
	 */
	private String signature;
	
	public PlaceOrderRequest() {
	}
	
	private PlaceOrderRequest(String address, String product, OrderType type, OrderSide side, BigDecimal price, BigDecimal amount) {
		this.address = address;
		this.product = product;
		this.type = type;
		this.side = side;
		this.price = price;
		this.amount = amount;
		this.signature = "";
		this.feeAddress = "";
		this.feeRateTaker = 0;
		this.feeRateMaker = 0;
	}
	
	public static PlaceOrderRequest limitBuy(String address, String product, BigDecimal price, BigDecimal amount) {
		return new PlaceOrderRequest(address, product, OrderType.Limit, OrderSide.Buy, price, amount);
	}
	
	public static PlaceOrderRequest limitSell(String address, String product, BigDecimal price, BigDecimal amount) {
		return new PlaceOrderRequest(address, product, OrderType.Limit, OrderSide.Sell, price, amount);
	}
	
	public static PlaceOrderRequest marketBuy(String address, String product, BigDecimal value) {
		return new PlaceOrderRequest(address, product, OrderType.Market, OrderSide.Buy, BigDecimal.ZERO, value);
	}
	
	public static PlaceOrderRequest marketSell(String address, String product, BigDecimal amount) {
		return new PlaceOrderRequest(address, product, OrderType.Market, OrderSide.Sell, BigDecimal.ZERO, amount);
	}
	
	public String getClientOrderId() {
		return clientOrderId;
	}
	
	public void setClientOrderId(String clientOrderId) {
		this.clientOrderId = clientOrderId;
	}
	
	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getProduct() {
		return product;
	}

	public void setProduct(String product) {
		this.product = product;
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
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}
	
	public void validate(Product product) {
		this.validate(product, false);
	}
	
	public void validate(Product product, boolean ignoreSignature) {
		if (this.clientOrderId != null && this.clientOrderId.length() > Order.MAX_CLIENT_ORDER_ID_LEN) {
			throw BusinessException.validateFailed("client order id length exceed %d", Order.MAX_CLIENT_ORDER_ID_LEN);
		}
		if (product.isDisabled()) {
			throw BusinessException.validateFailed("This product is disabled.");
		}
		Validators.validateAddress(this.address, AddressType.User, "user address");
		Validators.validateName(this.product, Product.MAX_LEN, "product");
		Validators.nonNull(this.type, "order type");
		Validators.nonNull(this.side, "order side");
		
		switch (this.type) {
		case Limit:
			if (product instanceof InstantExchangeProduct) {
				throw BusinessException.validateFailed("instant exchange only accept market order");
			}
			
			Validators.validateAmount(this.amount, product.getAmountPrecision(), product.getMinOrderAmount(), product.getMaxOrderAmount(), "amount");
			Validators.validateAmount(this.price, product.getPricePrecision(), BigDecimal.ZERO, Validators.LIMIT_UINT, "price");
			
			BigDecimal funds = Utils.mul(this.price, this.amount);
			if (funds.compareTo(product.getMinOrderFunds()) < 0) {
				throw BusinessException.validateFailed("price * amount is too small, minimum is %s", product.getMinOrderFunds().toPlainString());
			}
			
			break;
		case Market:
			if (this.price == null || this.price.compareTo(BigDecimal.ZERO) != 0) {
				throw BusinessException.validateFailed("price should be 0 for market order");
			}
			
			if (this.side == OrderSide.Buy) {
				Validators.validateAmount(this.amount, product.getFundsPrecision(), product.getMinOrderFunds(), null, "amount");
			} else {
				Validators.validateAmount(this.amount, product.getAmountPrecision(), product.getMinOrderAmount(), product.getMaxOrderAmount(), "amount");
			}
			
			break;
		default:
			throw new UnsupportedOperationException();
		}
		
		Validators.validateAddress(this.feeAddress, AddressType.User, "fee address");
		validateFeeRate(this.feeRateTaker);
		validateFeeRate(this.feeRateMaker);
		
		if (this.feeRateTaker < FEE_RATE_TAKER_MIN) {
			throw BusinessException.validateFailed("taker fee rate is less than %s", FEE_RATE_TAKER_MIN);
		}
		
		if (!ignoreSignature) {
			Validators.validateTimestamp(this.timestamp);
			Validators.validateSignature(this.signature);
		}
	}
	
	private static void validateFeeRate(double rate) {
		if (BigDecimal.valueOf(rate).scale() > 6) {
			throw BusinessException.validateFailed("fee rate precision is greater than 6");
		}
		
		if (rate < 0) {
			throw BusinessException.validateFailed("fee rate is negative");
		}
		
		if (rate >= 1) {
			throw BusinessException.validateFailed("fee rate should be less than 1");
		}
	}
	
	public Order toOrder(int productId, long userId) {
		Order order = new Order();
		
		order.setClientOrderId(this.clientOrderId);
		order.setProductId(productId);
		order.setUserId(userId);
		
		order.setType(this.type);
		order.setSide(this.side);
		order.setStatus(OrderStatus.New);
		
		order.setPrice(this.price);
		order.setAmount(this.amount);
		order.setFeeAddress(this.feeAddress);
		order.setFeeRateTaker(this.feeRateTaker);
		order.setFeeRateMaker(this.feeRateMaker);
		order.setFilledAmount(BigDecimal.ZERO);
		order.setFilledFunds(BigDecimal.ZERO);
		
		order.setTimestamp(this.timestamp);
		order.setSignature(this.signature);
		
		return order;
	}
}
