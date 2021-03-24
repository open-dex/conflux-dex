package conflux.dex.blockchain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Type;

import conflux.dex.blockchain.crypto.legacy.RpcEncodable;

public class TypedDataDynamicArray<T extends Type<?> & RpcEncodable> extends DynamicArray<T> implements RpcEncodable {
	
	private List<T> items;
	
	public TypedDataDynamicArray(Class<T> clz, List<T> items) {
		super(clz, items);
		
		this.items = items;
	}
	
	public TypedDataDynamicArray(Class<T> clz, T item) {
		this(clz, Arrays.asList(item));
	}

	@Override
	public List<Object> toArray() {
		return this.items.stream().map(i -> i.toArray()).collect(Collectors.toList());
	}
	
	// Fix web3j issue that encode to CancelRequest[], but it should be (....)[]
	@Override
	public String getTypeAsString() {
		return this.items.get(0).getTypeAsString() + "[]";
	}

}
