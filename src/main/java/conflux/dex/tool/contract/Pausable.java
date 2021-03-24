package conflux.dex.tool.contract;

import conflux.web3j.Account;

public interface Pausable extends Contract {
	
	default String pause(Account admin) throws Exception {
		return admin.call(this.getAddress(), "Pause");
	}
	
	default String resume(Account admin) throws Exception {
		return admin.call(this.getAddress(), "Resume");
	}

}
