package conflux.dex.event;

import java.util.List;
import java.util.Map;

import conflux.dex.common.Utils;
import conflux.dex.model.DepositRecord;

public class DepositEventArg {
	
	public List<DepositRecord> records;
	public Map<String, Long> users;
	
	public DepositEventArg(List<DepositRecord> records, Map<String, Long> users) {
		this.records = records;
		this.users = users;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

}
