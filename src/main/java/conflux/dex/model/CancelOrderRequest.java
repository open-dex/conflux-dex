package conflux.dex.model;

import java.sql.Timestamp;
import java.time.Instant;

import conflux.dex.common.Utils;

public class CancelOrderRequest {
	/**
	 * Order cancellation id. (auto-generated)
	 */
	private long id;
	/**
	 * Order id.
	 */
	private long orderId;
	/**
	 * Cancellation reason: "CustomerRequested", "MarketOrderPartialFilled", "OnChainForceWithdrawRequested", "AdminRequested".
	 */
	private CancelOrderReason reason;
	/**
	 * UNIX time in milliseconds.
	 */
	private long timestamp;
	/**
	 * Order cancellation signature.
	 */
	private String signature;
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
	
	public static CancelOrderRequest fromUser(long orderId, long timestamp, String signature) {
		CancelOrderRequest request = new CancelOrderRequest();
		
		request.orderId = orderId;
		request.reason = CancelOrderReason.CustomerRequested;
		request.timestamp = timestamp;
		request.signature = signature;
		
		return request;
	}
	
	public static CancelOrderRequest fromSystem(long orderId, CancelOrderReason reason) {
		CancelOrderRequest request = new CancelOrderRequest();
		
		request.orderId = orderId;
		request.reason = reason;
		request.timestamp = 0;
		request.signature = "";
		
		return request;
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}

	public long getOrderId() {
		return orderId;
	}

	public void setOrderId(long orderId) {
		this.orderId = orderId;
	}
	
	public CancelOrderReason getReason() {
		return reason;
	}
	
	public void setReason(CancelOrderReason reason) {
		this.reason = reason;
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
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
