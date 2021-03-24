package conflux.dex.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import conflux.dex.common.Utils;
import conflux.dex.controller.AddressTool;

public class DepositRecord {
	/**
	 * Deposit record id. (auto-generated)
	 */
	private long id;
	/**
	 * User address that deposit to.
	 */
	private String userAddress;
	private String userBase32Address;
	/**
	 * Deposit currency.
	 */
	private String currency;
	/**
	 * Deposit amount of currency.
	 */
	private BigDecimal amount;
	/**
	 * Sender of the deposit transaction on blockchain.
	 */
	private String txSender;
	private String txSenderBase32;
	/**
	 * Deposit transaction hash on blockchain.
	 */
	private String txHash;
	/**
	 * Record creation timestamp.
	 */
	private Timestamp createTime;
	
	public static DepositRecord create(String userAddress, String currency, BigDecimal amount, String txSender, String txHash) {
		DepositRecord record = new DepositRecord();
		
		record.userAddress = userAddress;
		record.currency = currency;
		record.amount = amount;
		record.txSender = txSender;
		record.txHash = txHash;
		record.createTime = Timestamp.from(Instant.now());
		
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
	
	public String getTxSender() {
		return txSender;
	}
	
	public void setTxSender(String txSender) {
		this.txSender = txSender;
		this.txSenderBase32 = AddressTool.toBase32(txSender);
	}

	public String getUserBase32Address() {
		return userBase32Address;
	}

	public void setUserBase32Address(String userBase32Address) {
		this.userBase32Address = userBase32Address;
	}

	public String getTxSenderBase32() {
		return txSenderBase32;
	}

	public void setTxSenderBase32(String txSenderBase32) {
		this.txSenderBase32 = txSenderBase32;
	}

	public String getTxHash() {
		return txHash;
	}
	
	public void setTxHash(String txHash) {
		this.txHash = txHash;
	}
	
	public Timestamp getCreateTime() {
		return createTime;
	}
	
	public void setCreateTime(Timestamp createTime) {
		this.createTime = createTime;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
