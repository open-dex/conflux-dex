package conflux.dex.blockchain.crypto;

import conflux.dex.common.Utils;

public class Entry {
	
	public String name;
	public String type;
	
	public Entry(String name, String type) {
		this.name = name;
		this.type = type;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
	
}