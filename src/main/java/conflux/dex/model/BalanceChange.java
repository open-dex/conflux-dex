package conflux.dex.model;

import java.math.BigDecimal;

import conflux.dex.common.Utils;

public class BalanceChange {
	/**
	 * Balance change type, including "OrderPlace", "OrderMatch", "OrderCancel",
	 * "Deposit", "Withdraw" and "Transfer".
	 */
	private BalanceChangeType type;
	/**
	 * The account id of the changed balance.
	 */
	private long accountId;
	/**
	 * The currency name of the changed balance.
	 */
	private String currency;
	/**
	 * Account balance (only exists when account balance changed).
	 */
	private BigDecimal balance;
	/**
	 * Available balance (only exists when available balance changed).
	 */
	private BigDecimal available;
	/**
	 * Change time, unix timestamp in milliseconds.
	 */
	private long changeTime;
	
	public static BalanceChange accountBalanceChanged(BalanceChangeType type, Account account) {
		BalanceChange change = new BalanceChange();
		
		change.type = type;
		change.accountId = account.getId();
		change.currency = account.getCurrency();
		change.balance = account.getAvailable().add(account.getHold());
		change.changeTime = System.currentTimeMillis();
		
		return change;
	}
	
	public static BalanceChange availableBalanceChanged(BalanceChangeType type, Account account) {
		BalanceChange change = new BalanceChange();
		
		change.type = type;
		change.accountId = account.getId();
		change.currency = account.getCurrency();
		change.available = account.getAvailable();
		change.changeTime = System.currentTimeMillis();
		
		return change;
	}
	
	public BalanceChangeType getType() {
		return type;
	}
	
	public void setType(BalanceChangeType type) {
		this.type = type;
	}

	public long getAccountId() {
		return accountId;
	}

	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public BigDecimal getAvailable() {
		return available;
	}

	public void setAvailable(BigDecimal available) {
		this.available = available;
	}

	public long getChangeTime() {
		return changeTime;
	}

	public void setChangeTime(long changeTime) {
		this.changeTime = changeTime;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
