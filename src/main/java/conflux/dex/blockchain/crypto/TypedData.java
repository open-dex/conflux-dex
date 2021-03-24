package conflux.dex.blockchain.crypto;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

import conflux.dex.blockchain.crypto.legacy.RpcEncodable;
import conflux.dex.common.BusinessException;
import conflux.dex.common.SignatureValidator;
import conflux.dex.model.EIP712Data;

public interface TypedData extends RpcEncodable {
	
	String primaryType();
	
	Map<String, List<Entry>> schemas();
	
	Domain domain();
	
	default byte[] hash() {
		Template template = new Template(this);
		
		try {
			return new StructuredDataEncoder(template.toJson()).hashStructuredData();
		} catch (IOException e) {
			throw BusinessException.internalError("failed to create StructuredDataEncoder", e);
		}
	}
	
	default String hashHex() {
		return Numeric.toHexString(this.hash());
	}
	
	default EIP712Data test() {
		Template template = new Template(this);
		
		try {
			byte[] hash = new StructuredDataEncoder(template.toJson()).hashStructuredData();
			return EIP712Data.create(template, Numeric.toHexString(hash));
		} catch (IOException e) {
			throw BusinessException.internalError("failed to create StructuredDataEncoder", e);
		}
	}
	
	default String validate(String signer, String signature) {
		byte[] hash = this.hash();
		SignatureValidator.DEFAULT.validate(signer, signature, hash, false);
		return Numeric.toHexString(hash);
	}

}
