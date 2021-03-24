package conflux.dex.ws;

import java.util.HashMap;

public class TopicRequest {
	
	private String topic;
	private boolean sub;
	private HashMap<String, Object> arguments;
	
	public String getTopic() {
		return topic;
	}
	
	public void setTopic(String topic) {
		this.topic = topic;
	}
	
	public boolean isSub() {
		return sub;
	}
	
	public void setSub(boolean sub) {
		this.sub = sub;
	}
	
	public HashMap<String, Object> getArguments() {
		return arguments;
	}

	public void setArguments(HashMap<String, Object> arguments) {
		this.arguments = arguments;
	}
	
	@Override
	public String toString() {
		return String.format("TopicRequest{sub=%s, topic=%s, args=%s}", this.sub, this.topic,  this.arguments);
	}

}
