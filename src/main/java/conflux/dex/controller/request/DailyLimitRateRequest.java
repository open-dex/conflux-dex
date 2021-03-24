package conflux.dex.controller.request;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.common.BusinessFault;

public class DailyLimitRateRequest extends AdminRequest {
	/**
	 * Product name.
	 */
	public String product;
	/**
	 * Limit rate of price upper bound.
	 */
	public double upperLimitRate;
	/**
	 * Limit rate of price lower bound.
	 */
	public double lowerLimitRate;
	/**
	 * Initial price of product.
	 */
	public BigDecimal initialPrice;
	
	public DailyLimitRateRequest() {
	}
	
	public DailyLimitRateRequest(String product, double upperLimitRate, double lowerLimitRate, BigDecimal initialPrice) {
		this.product = product;
		this.upperLimitRate = upperLimitRate;
		this.lowerLimitRate = lowerLimitRate;
		this.initialPrice = initialPrice;
	}
	
	@Override
	protected void validate() {
		if (upperLimitRate < 0 || lowerLimitRate < 0 || initialPrice.compareTo(BigDecimal.ZERO) <= 0) {
			throw BusinessFault.ProductInvalidDailyLimitParameter.rise();
		}
	}
	
	@Override
	protected List<RlpType> getEncodeValues() {
		return Arrays.asList(
				RlpString.create(product),
				RlpString.create(String.valueOf(upperLimitRate)),
				RlpString.create(String.valueOf(lowerLimitRate)),
				RlpString.create(initialPrice.toPlainString()));
	}
}
