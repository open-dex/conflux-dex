package conflux.dex.ws;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import conflux.dex.common.BusinessFault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.codahale.metrics.Meter;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.QueueMetric;

@Component
class SubscriberManager {
	
	private static final Logger logger = LoggerFactory.getLogger(SubscriberManager.class);
	
	private static final long intervalHeartbeatMillis = 5000;
	public static final long SessionTimeoutMillis = 30000;
	
	private ConcurrentMap<String, Subscriber> subscribers = new ConcurrentHashMap<String, Subscriber>();
	
	private QueueMetric subscribersQueue = Metrics.queue(SubscriberManager.class, "subscribers");
	private QueueMetric topicsQueue = Metrics.queue(SubscriberManager.class, "topics");
	private Meter invalidTopic = Metrics.meter(SubscriberManager.class, "topic.invalid");
	
	public void add(Subscriber subscriber) {
		this.subscribers.put(subscriber.getId(), subscriber);
		this.subscribersQueue.enqueue();
		logger.trace("subscriber added: {}", subscriber.getId());
	}
	
	public void remove(String subscriberId) {
		Subscriber subscriber = this.subscribers.remove(subscriberId);
		if (subscriber != null) {
			subscriber.getSubscribedTopics().clear();
			this.subscribersQueue.dequeue();
			logger.trace("subscriber removed: {}", subscriberId);
		}
	}
	
	public void onTopicRequest(String subscriberId, TopicRequest request) throws BusinessException {
		Subscriber subscriber = this.subscribers.get(subscriberId);
		if (subscriber == null) {
			return;
		}
		
		Topic topic = Topic.indexOf(request.getTopic());
		if (topic == null) {
			this.invalidTopic.mark();
			throw BusinessFault.TopicNotSupported.rise();
		}
		
		logger.trace("topic request: {}", request);
		
		if (request.isSub()) {
			topic.subscribe(subscriber, request);
			this.topicsQueue.enqueue();
		} else {
			topic.unsubscribe(subscriber, request);
			this.topicsQueue.dequeue();
		}
	}
	
	@Scheduled(initialDelay = intervalHeartbeatMillis, fixedRate = intervalHeartbeatMillis)
	public void heartbeat() {
		logger.trace("heartbeat: begin to check topics and send PING");
		
		long checkTopicTime = System.currentTimeMillis() - SessionTimeoutMillis;
		
		for (Subscriber subscriber : this.subscribers.values()) {
			if (checkTopicTime > subscriber.getConnectedTime() && subscriber.getSubscribedTopics().isEmpty()) {
				subscriber.close();
			} else {
				subscriber.ping();
			}
		}
		
		logger.trace("heartbeat: end");
	}

}
