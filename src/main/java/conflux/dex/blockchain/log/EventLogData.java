package conflux.dex.blockchain.log;

import java.math.BigInteger;
import java.util.Optional;

import conflux.dex.controller.AddressTool;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;

import conflux.dex.common.Utils;
import conflux.web3j.response.Log;

public class EventLogData {
	private conflux.web3j.types.Address contractAddress;
	private String txHash;
	
	protected EventLogData(Log log) {
		this.contractAddress = log.getAddress();
		
		Optional<String> txHash = log.getTransactionHash();
		this.txHash = txHash.isPresent() ? txHash.get() : "";
	}
	
	protected EventLogData(String contractAddress, String txHash) {
		this.contractAddress = AddressTool.address(contractAddress);
		this.txHash = txHash;
	}
	
	public conflux.web3j.types.Address getContractAddress() {
		return contractAddress;
	}
	
	public String getTxHash() {
		return txHash;
	}
	
	protected static String parseAddress(String encoded) {
		return ((Address) FunctionReturnDecoder.decodeIndexedValue(encoded, TypeReference.create(Address.class))).getValue();
	}
	
	protected static BigInteger parseUint256(String encoded) {
		return ((Uint256) FunctionReturnDecoder.decodeIndexedValue(encoded, TypeReference.create(Uint256.class))).getValue();
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
