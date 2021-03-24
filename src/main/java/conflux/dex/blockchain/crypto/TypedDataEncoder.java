package conflux.dex.blockchain.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

import conflux.dex.common.BusinessException;

/**
 * TypedDataEncoder is used to customize the encoding of struct to calculate EIP712 message hash.
 * This is because StructuredDataEncoder in web3j 4.6.3 has bug when encode struct with array.
 */
public class TypedDataEncoder extends StructuredDataEncoder {
	
	private EncodableTypedData data;
	
	private TypedDataEncoder(EncodableTypedData data) throws IOException {
		super(new Template(data).toJson());
		
		this.data = data;
	}
	
	public static byte[] hash(EncodableTypedData data) {
		try {
			return new TypedDataEncoder(data).hashStructuredData();
		} catch (IOException e) {
			throw BusinessException.internalError("failed to create TypedDataEncoder", e);
		}
	}
	
	public static String hashHex(EncodableTypedData data) {
		return Numeric.toHexString(hash(data));
	}
	
	@Override
	public byte[] encodeData(String primaryType, HashMap<String, Object> data) throws RuntimeException {
		return this.data.primaryType().equals(primaryType)
				? this.data.encode(this)
				: super.encodeData(primaryType, data);
	}
	
	static void append(ByteArrayOutputStream baos, Type<?> param) {
		String encodedHex = TypeEncoder.encode(param);
		byte[] encodedBytes = Numeric.hexStringToByteArray(encodedHex);
		baos.write(encodedBytes, 0, encodedBytes.length);
	}

}
