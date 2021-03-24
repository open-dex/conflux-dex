package conflux.dex.blockchain.crypto;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

class TypedBar implements EncodableTypedData {
	
	public static final TypedBar TEST_DATA = new TypedBar(TypedFoo.TEST_DATA, BigInteger.valueOf(777));
	
	public TypedFoo foo;
	public BigInteger nonce;
	
	public TypedBar(TypedFoo foo, BigInteger nonce) {
		this.foo = foo;
		this.nonce = nonce;
	}

	@Override
	public String primaryType() {
		return "Bar";
	}

	@Override
	public Map<String, List<Entry>> schemas() {
		Map<String, List<Entry>> schema = new HashMap<String, List<Entry>>();
		
		schema.put("Bar", Arrays.asList(
				new Entry("foo", this.foo.primaryType()),
				new Entry("nonce", "uint256")));
		
		schema.putAll(this.foo.schemas());
		
		return schema;
	}

	@Override
	public Domain domain() {
		return Domain.create("CRCL", "1.0", 7, "0x8aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
	}

	@Override
	public List<Type<?>> toFields(TypedDataEncoder encoder) {
		return Arrays.asList(
				this.field(encoder, this.foo),
				new Uint256(this.nonce));
	}

	@Override
	public List<Object> toArray() {
		throw new RuntimeException("should not test with nodejs");
	}

}
