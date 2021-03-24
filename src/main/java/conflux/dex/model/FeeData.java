package conflux.dex.model;

import java.math.BigDecimal;

import conflux.dex.common.Utils;

public class FeeData {
	
	/**
	 * Fee rate for taker order.
	 */
	private BigDecimal takerFeeRate = BigDecimal.ZERO;
	/**
	 * Fee rate for maker order.
	 */
	private BigDecimal makerFeeRate = BigDecimal.ZERO;
	/**
	 * Fee strategy: ForFree, FeeToDex or FeeToMaker.
	 */
	private FeeStrategy strategy = FeeStrategy.ForFree;
	/**
	 * Fee recipient for strategy FeeToDex.
	 */
	private String feeRecipient;
	
	public BigDecimal getTakerFeeRate() {
		return takerFeeRate;
	}
	
	public void setTakerFeeRate(BigDecimal takerFeeRate) {
		this.takerFeeRate = takerFeeRate;
	}
	
	public BigDecimal getMakerFeeRate() {
		return makerFeeRate;
	}
	
	public void setMakerFeeRate(BigDecimal makerFeeRate) {
		this.makerFeeRate = makerFeeRate;
	}
	
	public FeeStrategy getStrategy() {
		return strategy;
	}
	
	public void setStrategy(FeeStrategy strategy) {
		this.strategy = strategy;
	}
	
	public String getFeeRecipient() {
		return feeRecipient;
	}
	
	public void setFeeRecipient(String feeRecipient) {
		this.feeRecipient = feeRecipient;
	}
	
	public BigDecimal getTakerFee(BigDecimal amount) {
		return this.strategy == FeeStrategy.ForFree ? BigDecimal.ZERO : Utils.mul(amount, this.takerFeeRate);
	}
	
	public BigDecimal getMakerFee(BigDecimal amount) {
		return this.strategy == FeeStrategy.ForFree ? BigDecimal.ZERO : Utils.mul(amount, this.makerFeeRate);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof FeeData)) {
			return false;
		}
		
		FeeData other = (FeeData) obj;
		
		if (this.takerFeeRate.compareTo(other.takerFeeRate) != 0) {
			return false;
		}
		
		if (this.makerFeeRate.compareTo(other.makerFeeRate) != 0) {
			return false;
		}
		
		if (this.strategy != other.strategy) {
			return false;
		}
		
		if (this.feeRecipient == null) {
			return other.feeRecipient == null;
		} else {
			return this.feeRecipient.equals(other.feeRecipient);
		}
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

}
