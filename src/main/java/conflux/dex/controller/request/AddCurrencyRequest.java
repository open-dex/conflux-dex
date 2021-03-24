package conflux.dex.controller.request;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Validators;
import conflux.dex.model.Currency;
import conflux.web3j.types.AddressType;

public class AddCurrencyRequest extends AdminRequest {
	/**
	 * New currency to create.
	 */
	public Currency currency;
	
	public AddCurrencyRequest() {
	}
	
	public AddCurrencyRequest(Currency currency) {
		this.currency = currency;
	}
	
	@Override
	protected void validate() {
		Validators.validateName(this.currency.getName(), Currency.MAX_LEN, "currency name");
		Validators.validateAddress(this.currency.getContractAddress(), AddressType.Contract, "CRCL address");
		Validators.validateAddress(this.currency.getTokenAddress(), AddressType.Contract, "ERC777 address");
		
		if (this.currency.getDecimalDigits() != Currency.MAX_DECIMALS) {
			throw BusinessException.validateFailed("decimal digits should be %s", Currency.MAX_DECIMALS);
		}
		
		if (this.currency.getMinimumWithdrawAmount() == null) {
			throw BusinessException.validateFailed("min withdraw amount not specified");
		}
		
		if (this.currency.getMinimumWithdrawAmount().compareTo(BigDecimal.ZERO) != 0) {
			throw BusinessException.validateFailed("min withdraw amount should be zero");
		}
	}
	
	@Override
	protected List<RlpType> getEncodeValues() {
		return Arrays.asList(
				RlpString.create(this.currency.getName()),
				RlpString.create(this.currency.getContractAddress()),
				RlpString.create(this.currency.getTokenAddress()),
				RlpString.create(String.valueOf(this.currency.getDecimalDigits())),
				RlpString.create(this.currency.isCrossChain() ? "1" : "0"),
				RlpString.create(this.currency.getMinimumWithdrawAmount().toPlainString()));
	}
}
