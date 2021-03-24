package conflux.dex.blockchain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Uint256;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.blockchain.crypto.Entry;
import conflux.dex.blockchain.crypto.TypedData;

public class TypedOrderCancellation extends StaticStruct implements TypedData {
	
	private static final String PRIMARY_TYPE = "CancelRequest";
	
	private static final Map<String, List<Entry>> SCHEMAS = new HashMap<String, List<Entry>>();
	
	static {
		SCHEMAS.put(PRIMARY_TYPE, Arrays.asList(
				new Entry("order", TypedOrder.PRIMARY_TYPE),
				new Entry("nonce", "uint256")));
		SCHEMAS.put(TypedOrder.PRIMARY_TYPE, TypedOrder.SCHEMA);
	}
	
	public TypedOrder order;
	public long nonce;
	
	public TypedOrderCancellation(TypedOrder order, long nonce) {
		super(order, new Uint256(nonce));
		
		this.order = order;
		this.nonce = nonce;
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
		return Domain.boomflow();
	}
	
	@Override
	public List<Object> toArray() {
		return Arrays.asList(
				this.order.toArray(),
				this.nonce);
	}
}
