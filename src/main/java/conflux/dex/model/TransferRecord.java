package conflux.dex.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import conflux.dex.common.Utils;

public class TransferRecord {
	/**
	 * Withdraw record id. (auto-generated)
	 */
	private long id;
	/**
	 * User address to transfer.
	 */
	private String userAddress;
	/**
	 * Currency to transfer.
	 */
	private String currency;
	/**
	 * Recipients to receive the asset.
	 * Key is recipient address, and value is transfer amount.
	 */
	private Map<String, BigDecimal> recipients;
	/**
	 * UNIX time in milliseconds.
	 */
	private long timestamp;
	/**
	 * EIP712 message hash.
	 */
	private String hash;
	/**
	 * Request signature.
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
	
	public static TransferRecord request(String userAddress, String currency, Map<String, BigDecimal> recipients, long timestamp, String signature) {
		TransferRecord record = new TransferRecord();
		
		record.userAddress = userAddress;
		record.currency = currency;
		record.recipients = recipients;
		record.timestamp = timestamp;
		record.signature = signature;
		
		return record;
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public String getUserAddress() {
		return userAddress;
	}
	
	public void setUserAddress(String userAddress) {
		this.userAddress = userAddress;
	}
	
	public String getCurrency() {
		return currency;
	}
	
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	
	public Map<String, BigDecimal> getRecipients() {
		return recipients;
	}
	
	public void setRecipients(Map<String, BigDecimal> recipients) {
		this.recipients = recipients;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getHash() {
		return hash;
	}
	
	public void setHash(String hash) {
		this.hash = hash;
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
