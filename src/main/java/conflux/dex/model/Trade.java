package conflux.dex.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import conflux.dex.common.Utils;

public class Trade {
	/**
	 * Trade id. (auto-generated)
	 */
	private long id;
	/**
	 * Referenced product id.
	 */
	private int productId;
	/**
	 * Referenced taker order id.
	 */
	private long takerOrderId;
	/**
	 * Referenced maker order id.
	 */
	private long makerOrderId;
	/**
	 * Trade price from maker order.
	 */
	private BigDecimal price;
	/**
	 * Trade amount.
	 */
	private BigDecimal amount;
	/**
	 * Taker order side: "Buy", "Sell".
	 */
	private OrderSide side;
	/**
	 * Trade fee of taker side.
	 * For "Buy" order, it is base currency.
	 * For "Sell" order, it is quote currency.
	 */
	private BigDecimal takerFee;
	/**
	 * Trade fee of maker side.
	 * For "Buy" order, it is base currency.
	 * For "Sell" order, it is quote currency.
	 */
	private BigDecimal makerFee;
	/**
	 * Settlement status: "OffChainSettled", "OnChainSettled", "OnChainConfirmed".
	 */
	private SettlementStatus status = SettlementStatus.OffChainSettled;
	/**
	 * Transaction hash of settlement on blockchain.
	 */
	private String txHash;
	/**
	 * Transaction nonce of settlement on blockchain.
	 */
	private long txNonce;
	/**
	 * Create timestamp.
	 */
	private Timestamp createTime = Timestamp.from(Instant.now());
	/**
	 * Update timestamp.
	 */
	private Timestamp updateTime = this.createTime;
	
	public Trade() {}
	
	public Trade(int productId, long takerOrderId, long makerOrderId, BigDecimal price, BigDecimal amount, OrderSide side, BigDecimal takerFee, BigDecimal makerFee) {
		this.productId = productId;
		this.takerOrderId = takerOrderId;
		this.makerOrderId = makerOrderId;
		this.price = price;
		this.amount = amount;
		this.side = side;
		this.takerFee = takerFee;
		this.makerFee = makerFee;
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

	public long getTakerOrderId() {
		return takerOrderId;
	}

	public void setTakerOrderId(long takerOrderId) {
		this.takerOrderId = takerOrderId;
	}

	public long getMakerOrderId() {
		return makerOrderId;
	}

	public void setMakerOrderId(long makerOrderId) {
		this.makerOrderId = makerOrderId;
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

	public OrderSide getSide() {
		return side;
	}

	public void setSide(OrderSide side) {
		this.side = side;
	}
	
	public BigDecimal getTakerFee() {
		return takerFee;
	}
	
	public void setTakerFee(BigDecimal takerFee) {
		this.takerFee = takerFee;
	}
	
	public BigDecimal getMakerFee() {
		return makerFee;
	}
	
	public void setMakerFee(BigDecimal makerFee) {
		this.makerFee = makerFee;
	}
	
	public SettlementStatus getStatus() {
		return status;
	}

	public void setStatus(SettlementStatus status) {
		this.status = status;
	}
	
	public String getTxHash() {
		return txHash;
	}

	public void setTxHash(String txHash) {
		this.txHash = txHash;
	}
	
	public long getTxNonce() {
		return txNonce;
	}
	
	public void setTxNonce(long txNonce) {
		this.txNonce = txNonce;
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
	
	public BigDecimal getFunds() {
		return Utils.mul(this.price, this.amount);
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
