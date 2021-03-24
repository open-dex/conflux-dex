package conflux.dex.blockchain.crypto;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Hash;

public interface EncodableTypedData extends TypedData {
	
	default byte[] encode(TypedDataEncoder encoder) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		byte[] typeHash = encoder.typeHash(this.primaryType());
		TypedDataEncoder.append(baos, new Bytes32(typeHash));
		
		for (Type<?> field : this.toFields(encoder)) {
			TypedDataEncoder.append(baos, field);
		}
		
		return baos.toByteArray();
	}
	
	List<Type<?>> toFields(TypedDataEncoder encoder);
	
	default Bytes32 field(TypedDataEncoder encoder, EncodableTypedData data) {
		return new Bytes32(Hash.sha3(data.encode(encoder)));
	}
	
	default Bytes32 field(List<Type<?>> array) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		for (Type<?> element : array) {
			TypedDataEncoder.append(baos, element);
		}
		
		return new Bytes32(Hash.sha3(baos.toByteArray()));
	}
	
	@Override
	default byte[] hash() {
		return TypedDataEncoder.hash(this);
	}

}
