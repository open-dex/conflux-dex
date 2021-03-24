package conflux.dex.blockchain.crypto;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

import conflux.dex.blockchain.TypedTransfer;

public class TypedDataEncoderTest {
	
	@Test
	public void testSimple() throws IOException {
		StructuredDataEncoder encoder = new StructuredDataEncoder(new Template(TypedFoo.TEST_DATA).toJson());
		String hash = Numeric.toHexString(encoder.hashStructuredData());
		Assert.assertEquals(hash, TypedDataEncoder.hashHex(TypedFoo.TEST_DATA));
	}
	
	@Test
	public void testComposite() throws IOException {
		StructuredDataEncoder encoder = new StructuredDataEncoder(new Template(TypedBar.TEST_DATA).toJson());
		String hash = Numeric.toHexString(encoder.hashStructuredData());
		Assert.assertEquals(hash, TypedDataEncoder.hashHex(TypedBar.TEST_DATA));
	}
	
	@Test
	public void testTypedTransfer() {
		// web3j has bug to calculate hash of struct with array,
		// so, use nodejs to get the encoded hash
		String hash = "0xfa0be9960bf6990e2a2ddb7f05744175751a486845de00bd0c889df534f017fe";
		
		TypedTransfer data = new TypedTransfer(
				"0x1000000000000000000000000000000000000001",
				Arrays.asList(BigInteger.valueOf(1), BigInteger.valueOf(2)),
				Arrays.asList("0x1000000000000000000000000000000000000002", "0x1000000000000000000000000000000000000003"),
				666,
				"0x8aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1") {
			
			@Override
			public Domain domain() {
				return Domain.create("CRCL", "1.0", 7, "0x8aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1");
			}
			
		};
		
		Assert.assertEquals(hash, TypedDataEncoder.hashHex(data));
	}

}
