package conflux.dex.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Uint256;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.blockchain.crypto.Entry;
import conflux.dex.blockchain.crypto.TypedData;
import conflux.dex.model.Currency;
import conflux.dex.model.WithdrawRecord;

public class TypedWithdraw extends StaticStruct implements TypedData {
	
	private static final String PRIMARY_TYPE = "WithdrawRequest";
	
	private static final Map<String, List<Entry>> SCHEMAS = Collections.singletonMap(PRIMARY_TYPE, Arrays.asList(
			new Entry("userAddress", "address"),
			new Entry("amount", "uint256"),
			new Entry("recipient", "address"),
			new Entry("burn", "bool"),
			new Entry("nonce", "uint256")));
	
	public String userAddress;
	public BigInteger amount;
	public String recipient;
	public boolean burn;
	public long nonce;
	
	private String contractAddress;
	
	public TypedWithdraw(String userAddress, BigInteger amount, String recipient, boolean burn, long nonce, String crcl) {
		super(new Address(userAddress), new Uint256(amount), new Address(recipient), new Bool(burn), new Uint256(nonce));
		
		this.userAddress = userAddress;
		this.amount = amount;
		this.recipient = recipient;
		this.burn = burn;
		this.nonce = nonce;
		
		this.contractAddress = crcl;
	}
	
	public static TypedWithdraw create(WithdrawRecord record, Currency currency) {
		return new TypedWithdraw(
				record.getUserAddress(),
				currency.toIntegerFormat(record.getAmount()),
				record.getDestination(),
				record.isBurn(),
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
				this.burn,
				this.nonce);
	}

}
