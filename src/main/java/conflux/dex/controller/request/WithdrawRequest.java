package conflux.dex.controller.request;

import java.math.BigDecimal;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Utils;
import conflux.dex.common.Validators;
import conflux.dex.model.Currency;
import conflux.dex.model.WithdrawRecord;
import conflux.web3j.types.AddressType;

public class WithdrawRequest {
	
	/**
	 * User address.
	 */
	public String userAddress;
	/**
	 * Currency to withdraw.
	 */
	public String currency;
	/**
	 * The amount of currency to withdraw.
	 */
	public BigDecimal amount;
	/**
	 * The destination address to withdraw.
	 */
	public String recipient;
	/**
	 * Whether to burn tokens after withdrawal to ERC777 contract. If false, withdraw tokens to
	 * ERC777 contract on Conflux. If true, different tokens have different behaviors as following:
	 * 1) For cross-chain tokens, e.g. cETH, cBTC, withdraw back to the original blockchain.
	 * 2) For CFX tokens, withdraw back to CFX account on Conflux.
	 * 3) Otherwise, burn should be false.
	 */
	public boolean burn;
	/**
	 * Optional relay contract address to withdraw. E.g. Withdraw ETH/USDT to any defi contract on Ethereum.
	 * For assets on Conflux chain, it could be null or empty.
	 * For cross chain assets, use zero address 0x0000...0000 (of length 42 with 0x prefix) if not required.
	 */
	public String relayContract;
	/**
	 * Expected withdrawal fee.
	 */
	public BigDecimal fee;
	/**
	 * UNIX time in milliseconds.
	 */
	public long timestamp;
	/**
	 * Signature of the withdraw request.
	 */
	public String signature;
	
	public boolean isCrossChain() {
		return this.burn && !Currency.CFX.equalsIgnoreCase(this.currency);
	}
	
	public void validate() {
		Validators.validateAddress(this.userAddress, AddressType.User, "address");
		Validators.validateName(this.currency, Currency.MAX_LEN, "currency");
		Validators.validateAmount(this.amount, Currency.MAX_DECIMALS, null, null, "amount");
		if (this.isCrossChain()) {
			Validators.validateETHAddress(this.relayContract, "relay contract");
		} else {
			Validators.validateAddress(this.recipient, AddressType.User, "recipient");
		}
		Validators.nonNull(this.fee, "fee");
		if (this.fee.compareTo(this.amount) > 0) {
			throw BusinessException.validateFailed("withdraw amount is not enough for fee");
		}
		Validators.validateTimestamp(this.timestamp);
		Validators.validateSignature(this.signature);
	}
	
	public WithdrawRecord toRecord() {
		return WithdrawRecord.create(
				this.userAddress, 
				this.currency, 
				this.amount, 
				this.recipient, 
				this.burn,
				this.relayContract,
				this.fee,
				this.timestamp,
				this.signature);
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

}
