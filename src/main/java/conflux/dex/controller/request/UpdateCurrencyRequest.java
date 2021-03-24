package conflux.dex.controller.request;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Validators;
import conflux.dex.model.Currency;

public class UpdateCurrencyRequest extends AdminRequest {
	
	/**
	 * Currency name to update.
	 */
	public String name;
	/**
	 * Minimum withdraw amount.
	 */
	public BigDecimal minimumWithdrawAmount;
	
	public UpdateCurrencyRequest() {
		this("", 0);
	}
	
	public UpdateCurrencyRequest(String name, double amount) {
		this(name, BigDecimal.valueOf(amount));
	}
	
	public UpdateCurrencyRequest(String name, BigDecimal amount) {
		this.name = name;
		this.minimumWithdrawAmount = amount;
	}

	@Override
	protected void validate() {
		Validators.validateName(this.name, Currency.MAX_LEN, "currency name");
		
		if (this.minimumWithdrawAmount.signum() < 0) {
			throw BusinessException.validateFailed("min withdraw amount is negative");
		}
	}

	@Override
	protected List<RlpType> getEncodeValues() {
		return Arrays.asList(
				RlpString.create(this.name),
				RlpString.create(this.minimumWithdrawAmount.toPlainString()));
	}

}
