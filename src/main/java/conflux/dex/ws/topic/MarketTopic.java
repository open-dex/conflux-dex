package conflux.dex.ws.topic;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import conflux.dex.ws.Subscriber;
import conflux.dex.ws.Topic;
import conflux.dex.ws.TopicRequest;
import conflux.dex.ws.TopicResponse;

abstract class MarketTopic extends Topic {
	
	// map<topicName, map<subscriberId, Subscriber>>
	private ConcurrentMap<String, ConcurrentMap<String, Subscriber>> index = new ConcurrentHashMap<String, ConcurrentMap<String, Subscriber>>();

	@Override
	public void subscribe(Subscriber subscriber, TopicRequest request) {
		String topic = request.getTopic();
		this.index.computeIfAbsent(topic, t -> new ConcurrentHashMap<String, Subscriber>());
		if (this.index.get(topic).putIfAbsent(subscriber.getId(), subscriber) == null) {
			subscriber.getSubscribedTopics().add(topic);
		}
	}
	
	@Override
	public void unsubscribe(Subscriber subscriber, TopicRequest request) {
		String topic = request.getTopic();
		ConcurrentMap<String, Subscriber> subscribers = this.index.get(topic);
		if (subscribers == null) {
			return;
		}
		
		Subscriber removed = subscribers.remove(subscriber.getId());
		if (removed == null) {
			return;
		}
		
		removed.getSubscribedTopics().remove(topic);
	}
	
	public boolean isSubscribed(String topic) {
		if (topic == null || topic.isEmpty()) {
			return false;
		}
		
		ConcurrentMap<String, Subscriber> subscribers = this.index.get(topic);
		return subscribers != null && !subscribers.isEmpty();
	}

	public void publish(String topic, Object data) {
		if (topic == null || topic.isEmpty()) {
			return;
		}
		
		ConcurrentMap<String, Subscriber> subscribers = this.index.get(topic);
		if (subscribers == null) {
			return;
		}
		
		Iterator<Entry<String, Subscriber>> iterator = subscribers.entrySet().iterator();
		while (iterator.hasNext()) {
			Subscriber subscriber = iterator.next().getValue();
			if (subscriber.getSubscribedTopics().contains(topic)) {
				subscriber.consume(TopicResponse.create(topic, data));
			} else {
				iterator.remove();
			}
		}
	}

}
