package conflux.dex.ws;

import java.util.Set;

public interface Subscriber {
	
	String getId();
	
	long getConnectedTime();
	
	Set<String> getSubscribedTopics();
	
	void consume(TopicResponse data);
	
	void ping();
	
	void close();

}
