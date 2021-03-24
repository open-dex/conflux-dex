package conflux.dex.blockchain.log;

import java.math.BigInteger;

import conflux.web3j.response.Log;

public class ScheduleWithdrawRequest extends EventLogData {
	private String senderAddress;
	private BigInteger time;
	
	public ScheduleWithdrawRequest(Log log) {
		super(log);
		
		this.senderAddress = parseAddress(log.getTopics().get(1));
		this.time = parseUint256(log.getData());
	}
	
	public ScheduleWithdrawRequest(String contractAddress, String txHash, String sender, BigInteger time) {
		super(contractAddress, txHash);
		
		this.senderAddress = sender;
		this.time = time;
	}
	
	public String getSenderAddress() {
		return senderAddress;
	}

	public BigInteger getTime() {
		return time;
	}
}
