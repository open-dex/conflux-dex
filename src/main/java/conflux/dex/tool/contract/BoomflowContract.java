package conflux.dex.tool.contract;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import conflux.dex.blockchain.TypedOrder;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

import conflux.dex.blockchain.EncodeUtils;
import conflux.dex.common.Utils;
import conflux.web3j.Account;
import conflux.web3j.Cfx;
import conflux.web3j.contract.ContractCall;
import conflux.web3j.contract.abi.DecodeUtil;
import conflux.web3j.request.Epoch;
import conflux.web3j.response.Transaction;

public class BoomflowContract extends AbstractContract implements Pausable, Removable {
	
	public static class Order {
		public BigInteger max;
		public BigInteger filled;
		public boolean cancelled;
		public long timestamp;
		public boolean recorded;
		
		@Override
		public String toString() {
			return Utils.toJson(this);
		}
	}
	
	public BoomflowContract(Cfx cfx, String address) {
		super(cfx, address);
	}
	
	public Order getOrder(String hash, Epoch... epoch) {
		ContractCall call = this.createContractCall(epoch);
		Bytes32 orderHash = new Bytes32(Numeric.hexStringToByteArray(hash));
		
		Order order = new Order();
		order.max = DecodeUtil.decode(call.call("max", orderHash).sendAndGet(), Uint256.class);
		if (order.max == null) {
			return order;
		}
		order.filled = DecodeUtil.decode(call.call("filled", orderHash).sendAndGet(), Uint256.class);
		order.cancelled = DecodeUtil.decode(call.call("cancelled", orderHash).sendAndGet(), Bool.class);
		order.timestamp = DecodeUtil.decode(call.call("timestamps", orderHash).sendAndGet(), Uint256.class).longValueExact();
		order.recorded = DecodeUtil.decode(call.call("recorded", orderHash).sendAndGet(), Bool.class);
		
		return order;
	}
	
	public BigDecimal getTakerFeeRate(Epoch... epoch) {
		ContractCall call = this.createContractCall(epoch);	
		BigInteger rate = DecodeUtil.decode(call.call("takerFeePercentage").sendAndGet(), Uint256.class);
		return Utils.fromContractValue(rate);
	}
	
	public BigDecimal getMakerFeeRate(Epoch... epoch) {
		ContractCall call = this.createContractCall(epoch);	
		BigInteger rate = DecodeUtil.decode(call.call("makerFeePercentage").sendAndGet(), Uint256.class);
		return Utils.fromContractValue(rate);
	}
	
	public String recordOrders(Account admin, List<TypedOrder> hashes) throws Exception {
		return admin.call(this.getAddress(), "recordOrders", EncodeUtils.typedDatas2Array(hashes));
	}
	
	public List<String> parseDeleteObsoleteOrders(String txHash) throws Exception {
		Optional<Transaction> tx = this.getCfx().getTransactionByHash(txHash).sendAndGet();
		if (!tx.isPresent()) {
			throw new Exception("tx not found");
		}
		
		String data = tx.get().getData();
		if (!data.startsWith("0x28acc9e7")) {
			throw new Exception("invalid method signature");
		}
		
		// method_signature | offset | length
		if (data.length() < 10 + 64 + 64) {
			throw new Exception("data length too short");
		}
		
		data = data.substring(74); // skip method signature and 64 bytes' offset
		
		int count = Integer.parseInt(data.substring(0, 64), 16);
		data = data.substring(64);
		
		if (data.length() != count * 64) {
			throw new Exception("invalid data length");
		}
		
		List<String> orders = new ArrayList<String>(count);
		
		for (int i = 0; i < count; i++) {
			orders.add("0x" + data.substring(0, 64));
			data = data.substring(64);
		}
		
		return orders;
	}

}
