package conflux.dex.controller.request;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Utils;
import conflux.web3j.CfxUnit;

public class UpdateAdminAccountRequest extends AdminRequest {
	/**
	 * Administrator account nonce.
	 */
	public BigInteger nonce;
	/**
	 * Default gas price of administrator account.
	 */
	public BigInteger gasPrice;
	/**
	 * Default gas limit of administrator account.
	 */
	public BigInteger gasLimit;
	/**
	 * Default storage limit of administrator account.
	 */
	public BigInteger storageLimit;

	@Override
	protected void validate() {
		if (this.nonce != null && this.nonce.signum() <= 0) {
			throw BusinessException.validateFailed("nonce is negative");
		}
		
		if (this.gasPrice != null && this.gasPrice.signum() <= 0) {
			throw BusinessException.validateFailed("gas price should be positive");
		}
		
		if (this.gasLimit != null && this.gasLimit.compareTo(CfxUnit.DEFAULT_GAS_LIMIT) <= 0) {
			throw BusinessException.validateFailed("gas limit is too small");
		}
		
		if (this.storageLimit != null && this.storageLimit.signum() <= 0) {
			throw BusinessException.validateFailed("storage limit should be positive");
		}
	}

	@Override
	protected List<RlpType> getEncodeValues() {
		return Arrays.asList(
				this.encode(this.nonce),
				this.encode(this.gasPrice),
				this.encode(this.gasLimit),
				this.encode(this.storageLimit));
	}
	
	private RlpType encode(BigInteger value) {
		if (value == null) {
			value = BigInteger.ZERO;
		}
		
		return RlpString.create(value.toString());
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

}
