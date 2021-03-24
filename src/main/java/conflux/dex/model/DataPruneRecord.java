package conflux.dex.model;

import java.util.Comparator;

import conflux.dex.common.Utils;

public class DataPruneRecord {
	
	public static final Comparator<DataPruneRecord> IdComparator = new Comparator<DataPruneRecord>() {

		@Override
		public int compare(DataPruneRecord arg0, DataPruneRecord arg1) {
			return Long.compare(arg0.id, arg1.id);
		}
		
	};
	
	private long id;
	private long timestamp;
	private String hash;
	
	public DataPruneRecord(long id, long timestamp, String hash) {
		this.id = id;
		this.timestamp = timestamp;
		this.hash = hash;
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getHash() {
		return hash;
	}
	
	public void setHash(String hash) {
		this.hash = hash;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

}
