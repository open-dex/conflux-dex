package conflux.dex.worker;

import java.math.BigDecimal;

public class DepthPriceLevel {
	/**
	 * Order price.
	 */
	private BigDecimal price;
	/**
	 * Total amount in all orders.
	 */
	private BigDecimal amount;
	/**
	 * Total number of all orders.
	 */
	private int count;
	
	public DepthPriceLevel(BigDecimal price, BigDecimal amount) {
		this(price, amount, 1);
	}
	
	public DepthPriceLevel(BigDecimal price, BigDecimal amount, int count) {
		this.price = price;
		this.amount = amount;
		this.count = count;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public int getCount() {
		return count;
	}
	
	public void update(BigDecimal amountDelta, int countDelta) {
		synchronized (this) {
			this.amount = this.amount.add(amountDelta);
			this.count += countDelta;
		}
	}
	
	public DepthPriceLevel clone() {
		synchronized (this) {
			return new DepthPriceLevel(this.price, this.amount, this.count);
		}
	}
	
	@Override
	public String toString() {
		return String.format("%s{price=%s, amount=%s, count=%d}",
				this.getClass().getSimpleName(), this.price.toPlainString(), this.amount.toPlainString(), this.count);
	}
}
