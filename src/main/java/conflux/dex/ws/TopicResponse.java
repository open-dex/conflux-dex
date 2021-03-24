package conflux.dex.ws;

public class TopicResponse {
	
	private String topic;
	private long timestamp;
	private Object data;
	
	public static TopicResponse create(String topic, Object data) {
		TopicResponse response = new TopicResponse();
		
		response.topic = topic;
		response.timestamp = System.currentTimeMillis();
		response.data = data;
		
		return response;
	}
	
	public String getTopic() {
		return topic;
	}
	
	public void setTopic(String topic) {
		this.topic = topic;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public Object getData() {
		return data;
	}
	
	public void setData(Object data) {
		this.data = data;
	}

}
