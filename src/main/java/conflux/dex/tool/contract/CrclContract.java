package conflux.dex.tool.contract;

import conflux.dex.blockchain.EncodeUtils;
import conflux.dex.controller.AddressTool;
import conflux.web3j.Account;
import conflux.web3j.Cfx;
import conflux.web3j.contract.ContractCall;
import conflux.web3j.contract.abi.DecodeUtil;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;

public class CrclContract extends AbstractContract implements Pausable, Removable {
	
	public CrclContract(Cfx cfx, String address) {
		super(cfx, address);
	}

	public String getTokenAddress() {
		ContractCall call = this.createContractCall();
		String encodedResult = call.call("getTokenAddress").sendAndGet();
		return DecodeUtil.decode(encodedResult, Address.class);
	}


	public BigInteger getTimestamp(String txHash) {
		ContractCall call = this.createContractCall();
		String encodedResult = call.call("timestamps", EncodeUtils.hex2Bytes32(txHash)).sendAndGet();
		return DecodeUtil.decode(encodedResult, Uint256.class);
	}

	public static String setTokenAddress(String crcl, String tokenAddress, Account admin) throws Exception{
		Address addr = new Address(tokenAddress);
		String tx = admin.call(AddressTool.address(crcl), "setTokenAddress", addr);
		return tx;
	}
}
