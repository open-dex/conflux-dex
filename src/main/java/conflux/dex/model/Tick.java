package conflux.dex.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import conflux.dex.common.Utils;

public class Tick implements Cloneable {
	/**
	 * Tick id. (auto-generated)
	 */
	private long id;
	/**
	 * Referenced product id.
	 */
	private int productId;
	/**
	 * Tick granularity in minutes.
	 */
	private int granularity;
	/**
	 * Tick open value.
	 */
	private BigDecimal open;
	/**
	 * Tick high value.
	 */
	private BigDecimal high;
	/**
	 * Tick low value.
	 */
	private BigDecimal low;
	/**
	 * Tick close value.
	 */
	private BigDecimal close;
	/**
	 * Total volume of base currency.
	 */
	private BigDecimal baseCurrencyVolume;
	/**
	 * Total volume of quote currency.
	 */
	private BigDecimal quoteCurrencyVolume;
	/**
	 * Total number of trades.
	 */
	private int count;
	/**
	 * Tick creation timestamp, truncated.
	 */
	private Timestamp createTime;
	/**
	 * Last update timestamp.
	 */
	private Timestamp updateTime;
	
	public static Tick empty() {
		Tick tick = new Tick();
		tick.createTime = Timestamp.from(Instant.EPOCH);
		return tick;
	}
	
	public static Tick open(int productId, int granularity, BigDecimal price, BigDecimal amount, Instant timestamp) {
		Tick tick = new Tick();
		tick.productId = productId;
		tick.granularity = granularity;
		tick.open = price;
		tick.high = price;
		tick.low = price;
		tick.close = price;
		tick.baseCurrencyVolume = amount;
		tick.quoteCurrencyVolume = Utils.mul(price, amount);
		tick.count = 1;
		tick.createTime = Timestamp.from(timestamp);
		tick.updateTime = tick.createTime;
		return tick;
	}
	
	public static Tick placeholder(Tick previousTick, Timestamp timestamp) {
		Tick tick = new Tick();
		
		tick.productId = previousTick.productId;
		tick.granularity = previousTick.granularity;
		tick.open = previousTick.close;
		tick.high = previousTick.close;
		tick.low = previousTick.close;
		tick.close = previousTick.close;
		tick.baseCurrencyVolume = BigDecimal.ZERO;
		tick.quoteCurrencyVolume = BigDecimal.ZERO;
		tick.count = 0;
		tick.createTime = timestamp;
		tick.updateTime = tick.createTime;
		
		return tick;
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
	
	public int getGranularity() {
		return granularity;
	}
	
	public void setGranularity(int granularity) {
		this.granularity = granularity;
	}
	
	public BigDecimal getOpen() {
		return open;
	}
	
	public void setOpen(BigDecimal open) {
		this.open = open;
	}
	
	public BigDecimal getHigh() {
		return high;
	}
	
	public void setHigh(BigDecimal high) {
		this.high = high;
	}
	
	public BigDecimal getLow() {
		return low;
	}
	
	public void setLow(BigDecimal low) {
		this.low = low;
	}
	
	public BigDecimal getClose() {
		return close;
	}
	
	public void setClose(BigDecimal close) {
		this.close = close;
	}
	
	public BigDecimal getBaseCurrencyVolume() {
		return baseCurrencyVolume;
	}

	public void setBaseCurrencyVolume(BigDecimal baseCurrencyVolume) {
		this.baseCurrencyVolume = baseCurrencyVolume;
	}

	public BigDecimal getQuoteCurrencyVolume() {
		return quoteCurrencyVolume;
	}

	public void setQuoteCurrencyVolume(BigDecimal quoteCurrencyVolume) {
		this.quoteCurrencyVolume = quoteCurrencyVolume;
	}
	
	public int getCount() {
		return count;
	}
	
	public void setCount(int count) {
		this.count = count;
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
	
	public void update(Trade trade) {
		this.high = this.high.max(trade.getPrice());
		this.low = this.low.min(trade.getPrice());
		this.close = trade.getPrice();
		this.baseCurrencyVolume = this.baseCurrencyVolume.add(trade.getAmount());
		this.quoteCurrencyVolume = this.quoteCurrencyVolume.add(trade.getFunds());
		this.count++;
	}
	
	public void update(Tick tick) {
		this.high = this.high.max(tick.getHigh());
		this.low = this.low.min(tick.getLow());
		this.close = tick.getClose();
		this.baseCurrencyVolume = this.baseCurrencyVolume.add(tick.getBaseCurrencyVolume());
		this.quoteCurrencyVolume = this.quoteCurrencyVolume.add(tick.getQuoteCurrencyVolume());
		this.count += tick.getCount();
	}
	
	public Tick clone() {
		try {
			return (Tick) super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}
	
	public boolean isChanged(Tick tick) {
		return this.open.compareTo(tick.open) != 0
				|| this.high.compareTo(tick.high) != 0
				|| this.low.compareTo(tick.low) != 0
				|| this.close.compareTo(tick.close) != 0
				|| this.baseCurrencyVolume.compareTo(tick.baseCurrencyVolume) != 0
				|| this.quoteCurrencyVolume.compareTo(tick.quoteCurrencyVolume) != 0
				|| this.count != tick.count;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
