package conflux.dex.model;

import java.math.BigDecimal;

import conflux.dex.common.Utils;

public class DailyLimitRate {
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

	/**
	 * product id.
	 */
	private int productId;
	/**
	 * limit up rate (in percent).
	 */
	private double upperLimitRate;
	/**
	 * limit down rate (in percent).
	 */
	private double lowerLimitRate;
	/**
	 * initial closing price of product (only used in the first day of product).
	 */
	private BigDecimal initialPrice;
	
	public static DailyLimitRate newDailyLimitRate(int productId, double upperLimitRate, double lowerLimitRate, BigDecimal initialPrice) {
		DailyLimitRate dailyLimitRate = new DailyLimitRate();
		dailyLimitRate.productId = productId;
		dailyLimitRate.upperLimitRate = upperLimitRate;
		dailyLimitRate.lowerLimitRate = lowerLimitRate;
		dailyLimitRate.initialPrice = initialPrice;
		return dailyLimitRate;
	}

	public int getProductId() {
		return productId;
	}
	
	public void setProductId(int productId) {
		this.productId = productId;
	}
	
	public double getUpperLimitRate() {
		return upperLimitRate;
	}
	
	public void setUpperLimitRate(double upperLimitRate) {
		this.upperLimitRate = upperLimitRate;
	}
	
	public double getLowerLimitRate() {
		return lowerLimitRate;
	}
	
	public void setLowerLimitRate(double lowerLimitRate) {
		this.lowerLimitRate = lowerLimitRate;
	}

	public BigDecimal getInitialPrice() {
		return initialPrice;
	}

	public void setInitialPrice(BigDecimal initialPrice) {
		this.initialPrice = initialPrice;
	}
}