package conflux.dex.blockchain.log;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import conflux.dex.common.Utils;
import conflux.dex.controller.AddressTool;
import conflux.web3j.response.Log;

public class TransferData extends EventLogData {
	
	public static final String EVENT_HASH = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

	public String sender;
	public String recipient;
	public BigInteger amount;
	
	private TransferData(Log log) {
		super(log);
		
		this.sender = parseAddress(log.getTopics().get(1));
		this.recipient = parseAddress(log.getTopics().get(2));
		this.amount = parseUint256(log.getData());
	}
	
	public static Optional<TransferData> tryParse(Log log) {
		boolean matched = log.getTopics() != null
				&& log.getTopics().size() == 3
				&& log.getTopics().get(0).equalsIgnoreCase(EVENT_HASH);
		
		return matched ? Optional.of(new TransferData(log)) : Optional.empty();
	}
	
	public static String validate(Log log, String crcl, String sender, String recipient, BigDecimal amount) {
		Optional<TransferData> data = tryParse(log);
		
		if (!data.isPresent()) {
			return "failed to parse Transfer event";
		}
		
		return data.get().validate(crcl, sender, recipient, amount);
	}
	
	public String validate(String crcl, String sender, String recipient, BigDecimal amount) {
		String contractAddress = this.getContractAddress().getHexAddress();
		if (!contractAddress.equalsIgnoreCase(crcl)) {
			return String.format("CRCL address mismatch, onChain = %s, offChain = %s", contractAddress, crcl);
		}
		
		if (!this.sender.equalsIgnoreCase(sender)) {
			return String.format("sender address mismatch, onChain = %s, offChain = %s", this.sender, sender);
		}
		
		if (!this.recipient.equalsIgnoreCase(recipient)) {
			return String.format("recipient address mismatch, onChain = %s, offChain = %s", this.recipient, recipient);
		}
		
		BigInteger offChainAmount = Utils.toContractValue(amount);
		if (this.amount.compareTo(offChainAmount) != 0) {
			return String.format("transfer amount mismatch, onChain = %s, offChain = %s", this.amount, amount.toPlainString());
		}
		
		return null;
	}

}
