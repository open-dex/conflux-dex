package conflux.dex.blockchain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.blockchain.crypto.EncodableTypedData;
import conflux.dex.blockchain.crypto.Entry;
import conflux.dex.blockchain.crypto.TypedDataEncoder;
import conflux.dex.model.Currency;
import conflux.dex.model.TransferRecord;

public class TypedTransfer extends DynamicStruct implements EncodableTypedData {
	
	private static final String PRIMARY_TYPE = "TransferRequest";
	
	private static final Map<String, List<Entry>> SCHEMAS = Collections.singletonMap(PRIMARY_TYPE, Arrays.asList(
			new Entry("userAddress", "address"),
			new Entry("amounts", "uint256[]"),
			new Entry("recipients", "address[]"),
			new Entry("nonce", "uint256")));
	
	public String userAddress;
	public List<BigInteger> amounts;
	public List<String> recipients;
	public long nonce;
	
	private String contractAddress;
	
	public TypedTransfer(String userAddress, List<BigInteger> amounts, List<String> recipients, long nonce, String crcl) {
		super(new Address(userAddress),
				EncodeUtils.nums2Array(amounts),
				EncodeUtils.hex2AddressArray(recipients),
				new Uint256(nonce));
		
		this.userAddress = userAddress;
		this.amounts = amounts;
		this.recipients = recipients;
		this.nonce = nonce;
		this.contractAddress = crcl;
	}
	
	public static TypedTransfer create(TransferRecord record, Currency currency) {
		List<BigInteger> amounts = new ArrayList<BigInteger>(record.getRecipients().size());
		List<String> recipients = new ArrayList<String>(record.getRecipients().size());
		
		for (Map.Entry<String, BigDecimal> entry : record.getRecipients().entrySet()) {
			recipients.add(entry.getKey());
			amounts.add(currency.toIntegerFormat(entry.getValue()));
		}
		
		return new TypedTransfer(record.getUserAddress(), amounts, recipients, record.getTimestamp(), currency.getContractAddress());
	}

	@Override
	public String primaryType() {
		return PRIMARY_TYPE;
	}

	@Override
	public Map<String, List<Entry>> schemas() {
		return SCHEMAS;
	}
	
	@Override
	public Domain domain() {
		return Domain.getCRCL(this.contractAddress);
	}

	@Override
	public List<Type<?>> toFields(TypedDataEncoder encoder) {
		return Arrays.asList(
				new Address(this.userAddress),
				this.field(this.amounts.stream().map(Uint256::new).collect(Collectors.toList())),
				this.field(this.recipients.stream().map(Address::new).collect(Collectors.toList())),
				new Uint256(this.nonce));
	}
	
	// Fix web3j issue that encode to (address,dynamicarray,dynamicarray,uint256)
	@Override
	public String getTypeAsString() {
		return "(address,uint256[],address[],uint256)";
	}
	
	@Override
	public List<Object> toArray() {
		return Arrays.asList(
				this.userAddress,
				this.amounts,
				this.recipients,
				this.nonce);
	}

}
