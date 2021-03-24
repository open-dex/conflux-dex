package conflux.dex.controller.request;

import java.util.ArrayList;
import java.util.List;

import conflux.dex.controller.AddressTool;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Numeric;

import conflux.dex.common.BusinessException;
import conflux.dex.common.SignatureValidator;
import conflux.dex.common.Utils;
import conflux.web3j.AccountManager;
import conflux.web3j.types.Address;
import conflux.web3j.types.AddressType;

abstract class AdminRequest {
	
	public static final long DEFAULT_TIMEOUT_MS = 30_000;
	private static final SignatureValidator ADMIN_VALIDATOR = new SignatureValidator();
	/**
	 * UNIX time in milliseconds.
	 */
	public long timestamp = System.currentTimeMillis();
	/**
	 * Signature of administrator.
	 */
	public String signature;
	
	protected abstract void validate();
	protected abstract List<RlpType> getEncodeValues();
	
	public void validate(String signer) {
		long now = System.currentTimeMillis();
		if (this.timestamp < now - DEFAULT_TIMEOUT_MS || this.timestamp > now + DEFAULT_TIMEOUT_MS) {
			throw BusinessException.validateFailed("timestamp in signature is invalid, delta(ts-now) = %s", this.timestamp - now);
		}
		
		this.validate();

		validateSign(signer);
	}

	public void validateSign(String signer) {
		ADMIN_VALIDATOR.validate(signer, this.signature, this.encode());
	}

	public byte[] encode() {
		List<RlpType> values = new ArrayList<RlpType>(this.getEncodeValues());
		values.add(RlpString.create(String.valueOf(this.timestamp)));
		return RlpEncoder.encode(new RlpList(values));
	}
	
	public String encodeHex( ) {
		return Numeric.toHexString(this.encode());
	}
	
	public void sign(AccountManager am, String signer, String... password) throws Exception {
		AddressType.validateHexAddress(signer, AddressType.User);
		this.signature = am.signMessage(this.encode(), true, AddressTool.address(signer), password);
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
	
}
