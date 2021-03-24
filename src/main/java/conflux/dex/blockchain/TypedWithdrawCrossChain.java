package conflux.dex.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.blockchain.crypto.Entry;
import conflux.dex.blockchain.crypto.TypedData;
import conflux.dex.model.Currency;
import conflux.dex.model.WithdrawRecord;

public class TypedWithdrawCrossChain extends DynamicStruct implements TypedData {
	
	private static final String PRIMARY_TYPE = "WithdrawRequest";
	
	private static final Map<String, List<Entry>> SCHEMAS = Collections.singletonMap(PRIMARY_TYPE, Arrays.asList(
			new Entry("userAddress", "address"),
			new Entry("amount", "uint256"),
			new Entry("recipient", "string"),
			new Entry("defiRelayer", "address"),
			new Entry("fee", "uint256"),
			new Entry("nonce", "uint256")));
	
	public String userAddress;
	public BigInteger amount;
	public String recipient;
	public String defiRelayer;
	public BigInteger fee;
	public long nonce;
	
	private String contractAddress;
	
	public TypedWithdrawCrossChain(String userAddress, BigInteger amount, String recipient, String defiRelayer, BigInteger fee, long nonce, String crcl) {
		super(new Address(userAddress), new Uint256(amount), new Utf8String(recipient), new Address(defiRelayer), new Uint256(fee), new Uint256(nonce));
		
		this.userAddress = userAddress;
		this.amount = amount;
		this.recipient = recipient;
		this.defiRelayer = defiRelayer;
		this.fee = fee;
		this.nonce = nonce;
		
		this.contractAddress = crcl;
	}
	
	public static TypedWithdrawCrossChain create(WithdrawRecord record, Currency currency) {
		return new TypedWithdrawCrossChain(
				record.getUserAddress(),
				currency.toIntegerFormat(record.getAmount()),
				record.getDestination(),
				record.getRelayContract(),
				currency.toIntegerFormat(record.getFee()),
				record.getTimestamp(),
				currency.getContractAddress());
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
	public List<Object> toArray() {
		return Arrays.asList(
				this.userAddress,
				this.amount,
				this.recipient,
				this.defiRelayer,
				this.fee,
				this.nonce);
	}

}
