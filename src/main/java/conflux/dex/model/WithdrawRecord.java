package conflux.dex.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;

import conflux.dex.common.Utils;
import conflux.dex.controller.AddressTool;

public class WithdrawRecord {
	/**
	 * Withdraw record id. (auto-generated)
	 */
	private long id;
	/**
	 * Withdraw type: "OffChain", "OnChainRequest", "OnChainForce".
	 */
	private WithdrawType type;
	/**
	 * User address to withdraw.
	 */
	private String userAddress;
	private String userBase32Address;
	/**
	 * Withdraw currency.
	 */
	private String currency;
	/**
	 * Amount of currency to withdraw.
	 */
	private BigDecimal amount = BigDecimal.ZERO;
	/**
	 * Destination address to withdraw.
	 */
	private String destination;
	private String destinationBase32;
	/**
	 * Whether to burn tokens after withdrawal to ERC777 contract.
	 */
	private boolean burn;
	/**
	 * Optional relay contract address to withdraw. E.g. Withdraw ETH/USDT to any defi contract on Ethereum.
	 */
	private String relayContract;
	/**
	 * Expected withdrawal fee.
	 */
	private BigDecimal fee = BigDecimal.ZERO;
	/**
	 * Record timestamp (UNIX time in milliseconds).
	 */
	private long timestamp;
	/**
	 * Message hash in case of withdraw off chain.
	 */
	private String hash;
	/**
	 * Signature in case of withdraw off chain.
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
	
	private static WithdrawRecord create(WithdrawType type, String userAddress, String currency, String txHash) {
		WithdrawRecord record = new WithdrawRecord();
		
		record.type = type;
		record.userAddress = userAddress;
		record.currency = currency;
		
		if (txHash != null && !txHash.isEmpty()) {
			record.txHash = txHash;
			record.status = SettlementStatus.OnChainConfirmed;
		}
		
		return record;
	}

	public static WithdrawRecord create(String userAddress, String currency, BigDecimal amount, String destination, boolean burn, String relayContract, BigDecimal fee, long timestamp, String signature) {
		WithdrawRecord record = WithdrawRecord.create(WithdrawType.OffChain, userAddress, currency, null);
		record.amount = amount;
		record.destination = destination;
		record.burn = burn;
		record.relayContract = relayContract;
		record.fee = fee;
		record.timestamp = timestamp;
		record.signature = signature;
		return record;
	}
	
	public static WithdrawRecord request(String userAddress, String currency, String txHash, BigInteger time) {
		WithdrawRecord record = WithdrawRecord.create(WithdrawType.OnChainRequest, userAddress, currency, txHash);
		// use update_time to present the blockchain time
		record.updateTime = Timestamp.from(Instant.ofEpochSecond(time.longValue()));
		return record;
	}
	
	public static WithdrawRecord force(String userAddress, String currency, String txHash, BigDecimal amount) {
		WithdrawRecord record = WithdrawRecord.create(WithdrawType.OnChainForce, userAddress, currency, txHash);
		record.amount = amount;
		return record;
	}
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}
	
	public WithdrawType getType() {
		return type;
	}

	public void setType(WithdrawType type) {
		this.type = type;
	}

	public String getUserAddress() {
		return userAddress;
	}

	public void setUserAddress(String userAddress) {
		this.userAddress = userAddress;
		this.userBase32Address = AddressTool.toBase32(userAddress);
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public String getUserBase32Address() {
		return userBase32Address;
	}

	public void setUserBase32Address(String userBase32Address) {
		this.userBase32Address = userBase32Address;
	}

	public String getDestinationBase32() {
		if (destinationBase32 == null) {
			if (isCrossChain()) {
				destinationBase32 = destination;
			} else {
				// only convert conflux address.
				destinationBase32 = AddressTool.convertDestinationBase32(destination);
			}
		}
		return destinationBase32;
	}

	public void setDestinationBase32(String destinationBase32) {
		this.destinationBase32 = destinationBase32;
	}

	public boolean isBurn() {
		return burn;
	}
	
	public void setBurn(boolean burn) {
		this.burn = burn;
	}
	
	public String getRelayContract() {
		return relayContract;
	}
	
	public void setRelayContract(String relayContract) {
		this.relayContract = relayContract;
	}
	
	public BigDecimal getFee() {
		return fee;
	}
	
	public void setFee(BigDecimal fee) {
		this.fee = fee;
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
	
	public boolean isCrossChain() {
		return this.burn && !Currency.CFX.equalsIgnoreCase(this.currency);
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
