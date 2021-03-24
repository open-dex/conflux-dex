package conflux.dex.blockchain.log;

import java.math.BigInteger;

import conflux.web3j.response.Log;

public class DepositData extends EventLogData {
	private String senderAddress;
	private String recipientAddress;
	private BigInteger amount;
	
	public DepositData(Log log) {
		super(log);
		
		this.senderAddress = parseAddress(log.getTopics().get(1));
		this.recipientAddress = parseAddress(log.getTopics().get(2));
		this.amount = parseUint256(log.getData());
	}
	
	public DepositData(String contractAddress, String txHash, String sender, String recipient, BigInteger amount) {
		super(contractAddress, txHash);
		
		this.senderAddress = sender;
		this.recipientAddress = recipient;
		this.amount = amount;
	}
	
	public String getSenderAddress() {
		return senderAddress;
	}

	public String getRecipientAddress() {
		return recipientAddress;
	}

	public BigInteger getAmount() {
		return amount;
	}
}
