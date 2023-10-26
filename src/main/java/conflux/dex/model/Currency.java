package conflux.dex.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;

import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

import conflux.dex.common.Utils;
import conflux.dex.controller.AddressTool;

public class Currency {
	public static final int MAX_LEN = 32;
	public static final int MAX_DECIMALS = 18;
	private static final BigDecimal MAX_DECIMAL_POW = new BigDecimal(BigInteger.TEN.pow(MAX_DECIMALS));
	public static final String CFX = "CFX";
	public static final String FC = "FC";
	public static final String BTC = "BTC";
	public static final String ETH = "ETH";
	public static final String USDT = "USDT";

	public static final Comparator<Currency> NameComparator = new Comparator<Currency>() {

		@Override
		public int compare(Currency arg0, Currency arg1) {
			return arg0.name.compareToIgnoreCase(arg1.name);
		}
		
	};
	
	/**
	 * Currency id. (auto-generated)
	 */
	private int id;
	/**
	 * Currency name.
	 */
	@Size(min = 1, max = 32)
	private String name;
	/**
	 * CRCL contract address.
	 */
	@Size(min = 42, max = 42)
	private String contractAddress;
	protected String contractBase32Address;
	/**
	 * ERC777 contract address.
	 */
	@Size(min = 42, max = 42)
	private String tokenAddress;
	protected String tokenBase32Address;
	/**
	 * Currency decimal digits
	 */
	@PositiveOrZero
	private int decimalDigits;
	/**
	 * True to indicate a cross chain currency,
	 * which is allowed to withdraw to the original chain.
	 */
	private boolean crossChain;
	/**
	 * Minimum withdraw amount.
	 */
	private BigDecimal minimumWithdrawAmount;
	
	public Currency() {}
	
	public Currency(String name, String contractAddress, String tokenAddress, int decimalDigits) {
		this.name = name;
		this.contractAddress = contractAddress;
		this.tokenAddress = tokenAddress;
		this.decimalDigits = decimalDigits;
		this.minimumWithdrawAmount = BigDecimal.ZERO;
	}
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getContractAddress() {
		return contractAddress;
	}

	public void setContractAddress(String contractAddress) {
		this.contractAddress = contractAddress;
		if (contractAddress.length() == 42) {
			this.contractBase32Address = AddressTool.toBase32(contractAddress);
		}
	}
	
	public String getTokenAddress() {
		return tokenAddress;
	}
	
	public void setTokenAddress(String tokenAddress) {
		this.tokenAddress = tokenAddress;
		this.tokenBase32Address = AddressTool.toBase32(tokenAddress);
	}

	public String getContractBase32Address() {
		return contractBase32Address;
	}

	public void setContractBase32Address(String contractBase32Address) {
		this.contractBase32Address = contractBase32Address;
	}

	public String getTokenBase32Address() {
		return tokenBase32Address;
	}

	public void setTokenBase32Address(String tokenBase32Address) {
		this.tokenBase32Address = tokenBase32Address;
	}

	public int getDecimalDigits() {
		return decimalDigits;
	}

	public void setDecimalDigits(int decimalDigits) {
		this.decimalDigits = decimalDigits;
	}
	
	public boolean isCrossChain() {
		return crossChain;
	}
	
	public void setCrossChain(boolean crossChain) {
		this.crossChain = crossChain;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
	
	public BigDecimal toDecimalFormat(BigInteger num) {
		BigDecimal divisor = BigDecimal.TEN.pow(this.decimalDigits);
		return Utils.div(new BigDecimal(num), divisor, this.decimalDigits);
	}
	
	public BigInteger toIntegerFormat(BigDecimal num) {
		BigInteger pow = BigInteger.TEN.pow(this.decimalDigits);
		return Utils.mul(num, new BigDecimal(pow), this.decimalDigits).toBigIntegerExact();
	}
	
	public static BigInteger toBlockchainFormat(BigDecimal num) {
		return Utils.mul(num, MAX_DECIMAL_POW, MAX_DECIMALS).toBigIntegerExact();
	}

	public BigDecimal getMinimumWithdrawAmount() {
		return minimumWithdrawAmount;
	}

	public void setMinimumWithdrawAmount(BigDecimal minimumWithdrawAmount) {
		this.minimumWithdrawAmount = minimumWithdrawAmount;
	}
}
