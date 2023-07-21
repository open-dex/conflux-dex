package conflux.dex.common;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.utils.Numeric;

import com.codahale.metrics.Timer;

import conflux.web3j.types.Address;
import conflux.web3j.types.AddressType;

public class SignatureValidator {
	
	private static Timer perf = Metrics.timer(SignatureValidator.class, "perf");
	
	public static final SignatureValidator DEFAULT = new SignatureValidator();
	
	private boolean ignored;
	private AtomicBoolean disabled = new AtomicBoolean();
	
	public boolean isIgnored() {
		return ignored;
	}
	
	public void setIgnored(boolean ignored) {
		this.ignored = ignored;
	}
	
	public boolean isDisabled() {
		return disabled.get();
	}
	
	public void setDisabled(boolean disabled) {
		this.disabled.set(disabled);
	}
	
	public void validate(String signerAddress, String sigHex, byte[] message) {
		this.validate(signerAddress, sigHex, message, true);
	}
	
	public void validate(String signerAddress, String sigHex, byte[] message, boolean needToHash) {
		if (this.ignored) {
			return;
		}
		
		if (this.disabled.get()) {
			throw BusinessFault.SystemSuspended.rise();
		}
		
		AddressType.validateHexAddress(signerAddress, null);
		
		Validators.validateSignature(sigHex);
		
		long start = System.currentTimeMillis();
		
		byte[] sigBytes = Numeric.hexStringToByteArray(sigHex);
		SignatureData signatureData = new SignatureData(
				sigBytes[64],							// V
				Arrays.copyOfRange(sigBytes, 0, 32),	// R
				Arrays.copyOfRange(sigBytes, 32, 64));	// S
		
		BigInteger pubkey;
		try {
			pubkey = needToHash
					? Sign.signedMessageToKey(message, signatureData)
					: Sign.signedMessageHashToKey(message, signatureData);
		} catch (Exception e) {
			throw BusinessFault.SignatureParseFail.rise();
		}

		String recoveredOrigin = "0x"+Keys.getAddress(pubkey);
		String recoveredTransform = AddressType.User.normalize(Keys.getAddress(pubkey));
		if (!signerAddress.equalsIgnoreCase(recoveredTransform)
		&& !signerAddress.equalsIgnoreCase(recoveredOrigin)) {
			Logger logger = LoggerFactory.getLogger(getClass());
			logger.warn("SignatureInvalid, recovered {} expect {}", recoveredOrigin, signerAddress);
			throw BusinessFault.SignatureInvalid.rise();
		}
		
		Metrics.update(perf, start);
	}
	
}
