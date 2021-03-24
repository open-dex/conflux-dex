package conflux.dex.event;

import java.util.List;

import conflux.dex.model.Account;
import conflux.dex.model.TransferRecord;

public class TransferEventArg {
	
	public TransferRecord record;
	/**
	 * Sender account before transfer.
	 */
	public Account sender;
	/**
	 * Recipient accounts before transfer.
	 */
	public List<Account> recipients;
	
	public TransferEventArg(TransferRecord record, Account sender, List<Account> recipients) {
		this.record = record;
		this.sender = sender;
		this.recipients = recipients;
	}

}
