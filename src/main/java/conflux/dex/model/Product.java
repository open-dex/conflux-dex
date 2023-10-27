package conflux.dex.model;

import java.math.BigDecimal;

import javax.validation.constraints.Size;

import conflux.dex.common.Utils;

public class Product {
	public static final int MAX_LEN = 32;
	
	/**
	 * Product id. (auto-generated)
	 */
	private int id;
	/**
	 * Product name. (e.g. BTC-USDT)
	 */
	@Size(min = 1, max = 32)
	private String name;
	/**
	 * Referenced base currency id.
	 */
	private int baseCurrencyId;
	/**
	 * Referenced quote currency id.
	 */
	private int quoteCurrencyId;
	/**
	 * Quote currency precision when quote price (decimal places).
	 */
	private int pricePrecision;
	/**
	 * Base currency precision when quote amount (decimal places).
	 */
	private int amountPrecision;
	/**
	 * Quote currency precision when quote funds for market-buy orders (funds = price * amount).
	 */
	private int fundsPrecision;
	/**
	 * Minimum order amount for limit orders or market-sell orders.
	 */
	private BigDecimal minOrderAmount;
	/**
	 * Maximum order amount for limit orders or market-sell orders.
	 */
	private BigDecimal maxOrderAmount;
	/**
	 * Minimum order funds. (For limit orders, funds = order.price * order.amount; for market-buy orders, funds = order.amount)
	 */
	private BigDecimal minOrderFunds;

	private Boolean enable;
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getBaseCurrencyId() {
		return baseCurrencyId;
	}

	public void setBaseCurrencyId(int baseCurrencyId) {
		this.baseCurrencyId = baseCurrencyId;
	}

	public int getQuoteCurrencyId() {
		return quoteCurrencyId;
	}

	public void setQuoteCurrencyId(int quoteCurrencyId) {
		this.quoteCurrencyId = quoteCurrencyId;
	}

	public int getPricePrecision() {
		return pricePrecision;
	}

	public void setPricePrecision(int pricePrecision) {
		this.pricePrecision = pricePrecision;
	}

	public int getAmountPrecision() {
		return amountPrecision;
	}

	public void setAmountPrecision(int amountPrecision) {
		this.amountPrecision = amountPrecision;
	}

	public int getFundsPrecision() {
		return fundsPrecision;
	}

	public void setFundsPrecision(int fundsPrecision) {
		this.fundsPrecision = fundsPrecision;
	}

	public BigDecimal getMinOrderAmount() {
		return minOrderAmount;
	}

	public void setMinOrderAmount(BigDecimal minOrderAmount) {
		this.minOrderAmount = minOrderAmount;
	}

	public BigDecimal getMaxOrderAmount() {
		return maxOrderAmount;
	}

	public void setMaxOrderAmount(BigDecimal maxOrderAmount) {
		this.maxOrderAmount = maxOrderAmount;
	}

	public BigDecimal getMinOrderFunds() {
		return minOrderFunds;
	}

	public void setMinOrderFunds(BigDecimal minOrderFunds) {
		this.minOrderFunds = minOrderFunds;
	}

	public Boolean getEnable() {
		return enable;
	}

	public void setEnable(Boolean enable) {
		this.enable = enable;
	}

	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
