package conflux.dex.matching;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Optional;

import conflux.dex.common.Utils;
import conflux.dex.dao.DexDao;
import conflux.dex.model.Currency;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderType;
import conflux.dex.model.Product;
import conflux.dex.service.AccountService;

public class Order implements Cloneable {
	private long id;
	private boolean cancel;
	private OrderType type;
	private OrderSide side;
	private BigDecimal price;
	/**
	 * Order amount for "Limit" order type or "Market-Sell" order.
	 * For "Market-Buy" order, it is the total order value.
	 */
	private BigDecimal amount;
	
	// used to update the account when order matched
	private long baseAccountId;
	private long quoteAccountId;
	private long feeAccountId;
	private double feeRateTaker;
	private double feeRateMaker;
	
	private int productId;
	private long userId;
	private long timestamp;
	
	private boolean everMatched;
	
	// Order is completed when fully filled.
	// But for market buy order, there may be little drip left that unable to fill anymore.
	private boolean completed;
	private BigDecimal filledFunds = BigDecimal.ZERO;
	private BigDecimal filledAmount = BigDecimal.ZERO;

	private Order(long id, OrderType type, OrderSide side, BigDecimal price, BigDecimal amount) {
		this.id = id;
		this.type = type;
		this.side = side;
		this.price = price;
		this.amount = amount;
	}
	
	public static Order limitBuy(long id, BigDecimal price, BigDecimal amount) {
		return new Order(id, OrderType.Limit, OrderSide.Buy, price, amount);
	}
	
	public static Order limitSell(long id, BigDecimal price, BigDecimal amount) {
		return new Order(id, OrderType.Limit, OrderSide.Sell, price, amount);
	}
	
	public static Order marketBuy(long id, BigDecimal funds) {
		return new Order(id, OrderType.Market, OrderSide.Buy, BigDecimal.ZERO, funds);
	}
	
	public static Order marketSell(long id, BigDecimal amount) {
		return new Order(id, OrderType.Market, OrderSide.Sell, BigDecimal.ZERO, amount);
	}
	
	public static Order place(conflux.dex.model.Order o, long baseAccountId, long quoteAccountId, long feeAccountId) {
		BigDecimal amount = o.getAmount();
		if (o.getType() == OrderType.Limit || o.getSide() == OrderSide.Sell) {
			amount = amount.subtract(o.getFilledAmount());
		} else {
			amount = amount.subtract(o.getFilledFunds());
		}
		Order order = new Order(o.getId(), o.getType(), o.getSide(), o.getPrice(), amount);
		order.baseAccountId = baseAccountId;
		order.quoteAccountId = quoteAccountId;
		order.feeAccountId = feeAccountId;
		order.feeRateTaker = o.getFeeRateTaker();
		order.feeRateMaker = o.getFeeRateMaker();
		order.productId = o.getProductId();
		order.userId = o.getUserId();
		order.timestamp = o.getTimestamp();
		order.everMatched = o.isEverMatched();
		order.filledFunds = o.getFilledFunds();
		order.filledAmount = o.getFilledAmount();
		return order;
	}
	
	public static Order place(conflux.dex.model.Order o, DexDao dao) {
		Product product = dao.getProduct(o.getProductId()).mustGet();
		
		Currency baseCurrency = dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		Currency quoteCurrency = dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
		
		return place(o, dao, baseCurrency, quoteCurrency);
	}
	
	public static Order place(conflux.dex.model.Order o, DexDao dao, Currency baseCurrency, Currency quoteCurrency) {
		long baseAccountId = AccountService.mustGetAccount(dao, o.getUserId(), baseCurrency.getName()).getId();
		long quoteAccountId = AccountService.mustGetAccount(dao, o.getUserId(), quoteCurrency.getName()).getId();
		
		long feeOwnerId = dao.getUserByName(o.getFeeAddress()).mustGet().getId();
		Currency feeCurrency = o.getSide() == OrderSide.Buy ? baseCurrency : quoteCurrency;
		long feeAccountId = AccountService.mustGetAccount(dao, feeOwnerId, feeCurrency.getName()).getId();
		
		return place(o, baseAccountId, quoteAccountId, feeAccountId);
	}
	
	public static Order cancel(conflux.dex.model.Order o) {
		Order order = new Order(o.getId(), null, o.getSide(), BigDecimal.ZERO, BigDecimal.ZERO);
		order.cancel = true;
		order.productId = o.getProductId();
		order.everMatched = o.isEverMatched();
		return order;
	}
	
	public long getId() {
		return id;
	}
	
	public boolean isCancel() {
		return cancel;
	}

	public void setCancel(boolean cancel) {
		this.cancel = cancel;
	}
	
	public OrderType getType() {
		return type;
	}
	
	public OrderSide getSide() {
		return side;
	}
	
	public BigDecimal getPrice() {
		return price;
	}
	
	public BigDecimal getAmount() {
		return amount;
	}
	
	public long getBaseAccountId() {
		return baseAccountId;
	}

	public void setBaseAccountId(long baseAccountId) {
		this.baseAccountId = baseAccountId;
	}

	public long getQuoteAccountId() {
		return quoteAccountId;
	}

	public void setQuoteAccountId(long quoteAccountId) {
		this.quoteAccountId = quoteAccountId;
	}
	
	public long getFeeAccountId() {
		return feeAccountId;
	}
	
	public void setFeeAccountId(long feeAccountId) {
		this.feeAccountId = feeAccountId;
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
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public boolean isEverMatched() {
		return everMatched;
	}
	
	public void setEverMatched(boolean everMatched) {
		this.everMatched = everMatched;
	}
	
	public boolean isCompleted() {
		return completed;
	}
	
	public void setCompleted(boolean completed) {
		this.completed = completed;
	}
	
	public long getHoldAccountId() {
		return this.side == OrderSide.Buy ? this.quoteAccountId : this.baseAccountId;
	}
	
	public Order clone() {
		try {
			return (Order) super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}
	
	public boolean isFullyFilled() {
		return this.amount.compareTo(BigDecimal.ZERO) == 0;
	}
	
	public BigDecimal getUnfilled() {
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
	
	public Optional<BigDecimal> take(Order makerOrder, int amountScale) {
		if (this.side == makerOrder.side) {
			throw new IllegalArgumentException("invalid maker order side");
		}
		
		if (makerOrder.type == OrderType.Market) {
			throw new IllegalArgumentException("invalid maker order type");
		}
		
		// check if order price matches
		if (this.type == OrderType.Limit) {
			if (this.side == OrderSide.Buy && this.price.compareTo(makerOrder.price) < 0) {
				return Optional.empty();
			}
			
			if (this.side == OrderSide.Sell && this.price.compareTo(makerOrder.price) > 0) {
				return Optional.empty();
			}
		}
		
		// match successfully
		BigDecimal tradeAmount;
		
		if (this.type == OrderType.Limit || this.side == OrderSide.Sell) {
			tradeAmount = this.amount.min(makerOrder.amount);
			this.amount = this.amount.subtract(tradeAmount);
			
			if (this.amount.signum() == 0) {
				this.completed = true;
			}
		} else {
			// market buy with funds
			BigDecimal takerAmount = Utils.div(this.amount, makerOrder.price, amountScale);
			tradeAmount = takerAmount.min(makerOrder.amount);
			BigDecimal tradeFunds = Utils.mul(tradeAmount, makerOrder.price);
			this.amount = this.amount.subtract(tradeFunds);
			
			// e.g. only 1 drip unfilled for a market buy order,
			// and the actual trade amount will be truncated to 0
			// based on precision 18 on chain.
			if (tradeAmount.signum() == 0) {
				this.completed = true;
			}
		}
		
		makerOrder.amount = makerOrder.amount.subtract(tradeAmount);
		if (makerOrder.amount.signum() == 0) {
			makerOrder.completed = true;
		} else if (this.type == OrderType.Market && this.side == OrderSide.Buy) {
			/*
			 * Due to trade amount truncated by specific precision,
			 * the unfilled amount (or funds) could be still valid to
			 * buy 1 drip. Then, taker and maker orders match twice.
			 * 
			 * So, if the maker order is not fully filled, the taker order
			 * should be cancelled with little quote asset unfilled.
			 * 
			 * See unit test: testMarketBuyPrecisionIssue
			 */
			this.completed = true;
		}
		
		if (tradeAmount.signum() > 0) {
			this.everMatched = true;
			makerOrder.everMatched = true;
		}
		
		return Optional.of(tradeAmount);
	}

	public BigDecimal getFilledFunds() {
		return filledFunds;
	}

	public void setFilledFunds(BigDecimal filledFunds) {
		this.filledFunds = filledFunds;
	}

	public BigDecimal getFilledAmount() {
		return filledAmount;
	}

	public void setFilledAmount(BigDecimal filledAmount) {
		this.filledAmount = filledAmount;
	}

	@Override
	public String toString() {
		return String.format(
				"MatchingOrder{id=%d, %s | %s, price=%s, amount=%s, base/quoteAccount=%d/%d}", 
				this.id, this.type, this.side, 
				this.price.toPlainString(), this.amount.toPlainString(), 
				this.baseAccountId, this.quoteAccountId);
	}

	public static class BuyComparator implements Comparator<Order> {

		@Override
		public int compare(Order o1, Order o2) {
			int result = o1.price.compareTo(o2.price);
			if (result != 0) {
				return -result;
			}
			
			return Long.compare(o1.id, o2.id);
		}
		
	}
	
	public static class SellComparator implements Comparator<Order> {

		@Override
		public int compare(Order o1, Order o2) {
			int result = o1.price.compareTo(o2.price);
			if (result != 0) {
				return result;
			}
			
			return Long.compare(o1.id, o2.id);
		}
		
	}
}
