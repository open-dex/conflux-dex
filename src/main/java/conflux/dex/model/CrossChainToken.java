package conflux.dex.model;

import java.math.BigDecimal;

import conflux.dex.common.Utils;
import conflux.dex.controller.AddressTool;

public class CrossChainToken {
	public static BigDecimal fakeFee = null;
	/**
	 * Token (ERC777) address on Conflux.
	 */
	private String address;
	private String base32Address;
	/**
	 * Token name, e.g. btc, eth or ERC20 token address.
	 */
	private String name;
	/**
	 * Token decimals in original chain, e.g. BTC(8), USDT(6).
	 */
	private int decimals;
	/**
	 * Withdraw fee to sponsor.
	 */
	private BigDecimal withdrawFee;
	/**
	 * Minimum withdraw amount.
	 */
	private BigDecimal minWithdrawAmount;
	
	public String getAddress() {
		return address;
	}
	
	public void setAddress(String address) {
		this.address = address;
		this.base32Address = AddressTool.toBase32(address);
	}

	public String getBase32Address() {
		return base32Address;
	}

	public void setBase32Address(String base32Address) {
		this.base32Address = base32Address;
	}

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public int getDecimals() {
		return decimals;
	}
	
	public void setDecimals(int decimals) {
		this.decimals = decimals;
	}
	
	public BigDecimal getWithdrawFee() {
		return fakeFee == null ? withdrawFee : fakeFee ;
	}
	
	public void setWithdrawFee(BigDecimal withdrawFee) {
		this.withdrawFee = withdrawFee;
	}
	
	public BigDecimal getMinWithdrawAmount() {
		return minWithdrawAmount;
	}
	
	public void setMinWithdrawAmount(BigDecimal minWithdrawAmount) {
		this.minWithdrawAmount = minWithdrawAmount;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof CrossChainToken)) {
			return false;
		}
		
		CrossChainToken other = (CrossChainToken) obj;
		
		return this.name.equalsIgnoreCase(other.name)
				&& this.address.equalsIgnoreCase(other.address)
				&& this.decimals == other.decimals
				&& equals(this.withdrawFee, other.withdrawFee)
				&& equals(this.minWithdrawAmount, other.minWithdrawAmount);
	}
	
	private static boolean equals(BigDecimal a, BigDecimal b) {
		if (a == null && b == null) {
			return true;
		}
		
		if (a == null || b == null) {
			return false;
		}
		
		return a.compareTo(b) == 0;
	}

}
