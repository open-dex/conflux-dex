package conflux.dex.model;

public class OrderPruneRecord implements Comparable<OrderPruneRecord> {
	
	private long timestamp;
	private long orderId;
	private String hash;
	
	public static OrderPruneRecord create(long timestamp, long orderId, String hash) {
		OrderPruneRecord record = new OrderPruneRecord();
		record.timestamp = timestamp;
		record.orderId = orderId;
		record.hash = hash;
		return record;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public long getOrderId() {
		return orderId;
	}
	
	public void setOrderId(long orderId) {
		this.orderId = orderId;
	}
	
	public String getHash() {
		return hash;
	}
	
	public void setHash(String hash) {
		this.hash = hash;
	}

	@Override
	public int compareTo(OrderPruneRecord arg0) {
		if (this.timestamp < arg0.timestamp) {
			return -1;
		}
		
		if (this.timestamp > arg0.timestamp) {
			return 1;
		}
		
		return Long.compare(this.orderId, arg0.orderId);
	}

}
