package conflux.dex.tool.contract;

import java.util.Optional;

import conflux.dex.controller.AddressTool;
import conflux.web3j.Account;
import conflux.web3j.Cfx;
import conflux.web3j.contract.ContractCall;
import conflux.web3j.request.Epoch;
import conflux.web3j.response.Receipt;
import conflux.web3j.response.Transaction;
import conflux.web3j.types.Address;

public interface Contract {
	
	Cfx getCfx();
	
	String getAddressHex();

	conflux.web3j.types.Address getAddress();
	
	default ContractCall createContractCall(Epoch... epoch) {
		ContractCall call = new ContractCall(this.getCfx(), this.getAddress());
		
		if (epoch.length > 0) {
			call.buildEpoch(epoch[0]);
		}
		
		return call;
	}
	
	default String resendFailedTx(Account admin, String txHash) throws Exception {
		Optional<Transaction> tx = this.getCfx().getTransactionByHash(txHash).sendAndGet();
		if (!tx.isPresent()) {
			throw new Exception("tx not found");
		}
		
		Optional<Receipt> receipt = this.getCfx().getTransactionReceipt(txHash).sendAndGet();
		if (!receipt.isPresent()) {
			throw new Exception("tx not executed");
		}
		
		if (receipt.get().getOutcomeStatus() == 0) {
			throw new Exception("tx not failed");
		}
		
		return admin.call(this.getAddress(), tx.get().getData());
	}

}

class AbstractContract implements Contract {
	
	private Cfx cfx;
	private String address;
	private conflux.web3j.types.Address addressObj;

	public AbstractContract(Cfx cfx, String address) {
		this.cfx = cfx;
		this.address = address;
		this.addressObj = AddressTool.address(address);
	}

	@Override
	public Cfx getCfx() {
		return this.cfx;
	}

	@Override
	public String getAddressHex() {
		return this.address;
	}

	@Override
	public Address getAddress() {
		return addressObj;
	}
}
