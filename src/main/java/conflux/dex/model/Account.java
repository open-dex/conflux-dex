package conflux.dex.model;

import java.math.BigDecimal;

import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import conflux.dex.common.Utils;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Account {
	/**
	 * Account id. (auto-generated)
	 */
	private long id;
	/**
	 * Referenced user id.
	 */
	private long userId;
	/**
	 * Referenced currency name.
	 */
	@Size(min = 1, max = 32)
	private String currency;
	/**
	 * Balance held by unfilled orders.
	 */
	private BigDecimal hold;
	/**
	 * Balance available to place orders.
	 */
	private BigDecimal available;
	/**
	 * Account status: "Normal", "ForceWithdrawing".
	 */
	private AccountStatus status;
	
	public Account() {}
	
	public Account(long userId, String currency, BigDecimal balance) {
		this.userId = userId;
		this.currency = currency;
		this.hold = BigDecimal.ZERO;
		this.available = balance;
		this.status = AccountStatus.Normal;
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public long getUserId() {
		return userId;
	}
	
	public void setUserId(long userId) {
		this.userId = userId;
	}
	
	public String getCurrency() {
		return currency;
	}
	
	public void setCurrency(String currency) {
		this.currency = currency;
	}
	
	public BigDecimal getHold() {
		return hold;
	}
	
	public void setHold(BigDecimal hold) {
		this.hold = hold;
	}
	
	public BigDecimal getAvailable() {
		return available;
	}
	
	public void setAvailable(BigDecimal available) {
		this.available = available;
	}
	
	public AccountStatus getStatus() {
		return status;
	}

	public void setStatus(AccountStatus status) {
		this.status = status;
	}
	
	public BigDecimal getBalance() {
		return this.hold.add(this.available);
	}
	
	public String getHoldString() {
		return this.hold.stripTrailingZeros().toPlainString();
	}
	
	public String getAvailableString() {
		return this.available.stripTrailingZeros().toPlainString();
	}
	
	public String getBalanceString() {
		return this.hold.add(this.available).stripTrailingZeros().toPlainString();
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
