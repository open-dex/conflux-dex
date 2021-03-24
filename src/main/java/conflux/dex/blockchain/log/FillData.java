package conflux.dex.blockchain.log;

import java.math.BigInteger;
import java.util.Optional;

import org.web3j.utils.Numeric;

import conflux.web3j.contract.abi.TupleDecoder;
import conflux.web3j.response.Log;

public class FillData extends EventLogData {
	
	public static final String EVENT_HASH = "0x31e18216f8b2ca46fa286ccb3d0ee6bd32435c0bbe4108625808581e661c52ca";
	
	public String orderHash;			// order EIP712 hash
	public BigInteger fee;				// trade fee for order creator
	public BigInteger contractFee;		// trade fee for DEX matcher
	public String contractFeeAddress;	// Address to receive contract fee
	public String matcherAddress;		// DEX operator that submitted the order
	public BigInteger tradeAmount;		// total amount traded

	private FillData(Log log) {
		super(log);
		
		TupleDecoder decoder = new TupleDecoder(log.getData());
		
		this.orderHash = Numeric.prependHexPrefix(decoder.next());
		this.fee = decoder.nextUint256();
		this.contractFee = decoder.nextUint256();
		this.contractFeeAddress = decoder.nextAddress();
		this.matcherAddress = decoder.nextAddress();
		this.tradeAmount = decoder.nextUint256();
	}
	
	public static Optional<FillData> tryParse(Log log) {
		boolean matched = log.getTopics() != null
				&& log.getTopics().size() == 1
				&& log.getTopics().get(0).equalsIgnoreCase(EVENT_HASH);
		
		return matched ? Optional.of(new FillData(log)) : Optional.empty();
	}

}
