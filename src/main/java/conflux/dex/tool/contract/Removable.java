package conflux.dex.tool.contract;

import java.util.Arrays;
import java.util.List;

import org.web3j.abi.datatypes.generated.Uint256;

import conflux.dex.blockchain.EncodeUtils;
import conflux.web3j.Account;
import conflux.web3j.contract.ContractCall;
import conflux.web3j.contract.abi.DecodeUtil;
import conflux.web3j.request.Epoch;

public interface Removable extends Contract {
	
	default long getTimestamp(Epoch... epoch) {
		ContractCall call = this.createContractCall(epoch);
		return DecodeUtil.decode(call.call("timestamp").sendAndGet(), Uint256.class).longValueExact();
	}
	
	default String setTimestamp(Account admin, long timestamp) throws Exception {
		return admin.call(this.getAddress(), "setTimestamp", new Uint256(timestamp));
	}
	
	default String deleteObsoleteData(Account admin, String... hashes) throws Exception {
		return this.deleteObsoleteData(admin, Arrays.asList(hashes));
	}
	
	default String deleteObsoleteData(Account admin, List<String> hashes) throws Exception {
		return admin.call(this.getAddress(), "removeObsoleteData", EncodeUtils.hex2Bytes32Array(hashes));
	}

}
