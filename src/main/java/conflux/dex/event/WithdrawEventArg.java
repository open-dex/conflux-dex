package conflux.dex.event;

import conflux.dex.model.Account;
import conflux.dex.model.WithdrawRecord;

public class WithdrawEventArg {
	
	public WithdrawRecord record;
	/**
	 * Account before withdrawal
	 */
	public Account account;
	
	public WithdrawEventArg(WithdrawRecord record, Account account) {
		this.record = record;
		this.account = account;
	}

}
