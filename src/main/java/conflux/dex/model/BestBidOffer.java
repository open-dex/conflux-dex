package conflux.dex.model;

import java.math.BigDecimal;
import java.time.Instant;

import conflux.dex.common.Utils;

public class BestBidOffer {
	/**
	 * Product name.
	 */
	private String product;
	/**
	 * Quote timestamp.
	 */
	private Instant quoteTime;
	/**
	 * Best bid price.
	 */
	private BigDecimal bid;
	/**
	 * Best bid amount.
	 */
	private BigDecimal bidAmount;
	/**
	 * Best bid order count.
	 */
	private int bidCount;
	/**
	 * Best ask price.
	 */
	private BigDecimal ask;
	/**
	 * Best ask amount.
	 */
	private BigDecimal askAmount;
	/**
	 * Best ask order count.
	 */
	private int askCount;
	
	public BestBidOffer() {
		this("");
	}
	
	public BestBidOffer(String product) {
		this.product = product;
		this.quoteTime = Instant.now();
	}
	
	public String getProduct() {
		return product;
	}
	
	public void setProduct(String product) {
		this.product = product;
	}
	
	public Instant getQuoteTime() {
		return quoteTime;
	}
	
	public void setQuoteTime(Instant quoteTime) {
		this.quoteTime = quoteTime;
	}
	
	public BigDecimal getBid() {
		return bid;
	}
	
	public void setBid(BigDecimal bid) {
		this.bid = bid;
	}
	
	public BigDecimal getBidAmount() {
		return bidAmount;
	}
	
	public void setBidAmount(BigDecimal bidAmount) {
		this.bidAmount = bidAmount;
	}
	
	public BigDecimal getAsk() {
		return ask;
	}
	
	public void setAsk(BigDecimal ask) {
		this.ask = ask;
	}
	
	public BigDecimal getAskAmount() {
		return askAmount;
	}
	
	public void setAskAmount(BigDecimal askAmount) {
		this.askAmount = askAmount;
	}

	public int getBidCount() {
		return bidCount;
	}

	public void setBidCount(int bidCount) {
		this.bidCount = bidCount;
	}

	public int getAskCount() {
		return askCount;
	}

	public void setAskCount(int askCount) {
		this.askCount = askCount;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
