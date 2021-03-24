package conflux.dex.blockchain.crypto;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

class TypedFoo implements EncodableTypedData {
	
	public static final TypedFoo TEST_DATA = new TypedFoo(
			"0x1000000000000000000000000000000000000001",
			"0x1000000000000000000000000000000000000002",
			BigInteger.valueOf(999));
	
	public static final List<Entry> SCHEMA = Arrays.asList(
			new Entry("from", "address"),
			new Entry("to", "address"),
			new Entry("amount", "uint256"));
	
	public String from;
	public String to;
	public BigInteger amount;
	
	public TypedFoo(String from, String to, BigInteger amount) {
		this.from = from;
		this.to = to;
		this.amount = amount;
	}

	@Override
	public String primaryType() {
		return "Foo";
	}

	@Override
	public Map<String, List<Entry>> schemas() {
		return Collections.singletonMap("Foo", SCHEMA);
	}

	@Override
	public Domain domain() {
		return Domain.create("CRCL", "1.0", 7, "0x8aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
	}

	@Override
	public List<Type<?>> toFields(TypedDataEncoder encoder) {
		return Arrays.asList(
				new Address(this.from),
				new Address(this.to),
				new Uint256(this.amount));
	}
	
	@Override
	public List<Object> toArray() {
		throw new RuntimeException("should not test with nodejs");
	}

}
