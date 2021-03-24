package conflux.dex.ws.topic;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Validators;
import conflux.dex.dao.DexDao;
import conflux.dex.model.User;
import conflux.dex.ws.Subscriber;
import conflux.dex.ws.Topic;
import conflux.dex.ws.TopicRequest;
import conflux.dex.ws.TopicResponse;
import conflux.web3j.types.AddressType;

abstract class UserTopic extends Topic {
	
	private static final String KEY_ADDRESS = "address";
	
	protected static class Index {
		
		private String topic;
		private ConcurrentMap<Long, ConcurrentMap<String, Subscriber>> index = new ConcurrentHashMap<Long, ConcurrentMap<String, Subscriber>>();
		
		public Index(String topic) {
			this.topic = topic;
		}
		
		public void subscribe(Subscriber subscriber, long userId) {
			this.index.computeIfAbsent(userId, uid -> new ConcurrentHashMap<String, Subscriber>());
			
			if (this.index.get(userId).putIfAbsent(subscriber.getId(), subscriber) == null) {
				subscriber.getSubscribedTopics().add(this.topic);
			}
		}
		
		public void unsubscribe(Subscriber subscriber, long userId) {
			ConcurrentMap<String, Subscriber> subscribers = this.index.get(userId);
			if (subscribers == null) {
				return;
			}
			
			Subscriber removed = subscribers.remove(subscriber.getId());
			if (removed == null) {
				return;
			}
			
			if (subscribers.isEmpty()) {
				this.index.remove(userId);
			}
			
			removed.getSubscribedTopics().remove(this.topic);
		}
		
		public boolean isSubscribed(long userId) {
			ConcurrentMap<String, Subscriber> subscribers = this.index.get(userId);
			return subscribers != null && !subscribers.isEmpty();
		}
		
		public void publish(long userId, Object data) {
			ConcurrentMap<String, Subscriber> subscribers = this.index.get(userId);
			if (subscribers == null) {
				return;
			}
			
			Iterator<Entry<String, Subscriber>> iterator = subscribers.entrySet().iterator();
			while (iterator.hasNext()) {
				Subscriber subscriber = iterator.next().getValue();
				if (subscriber.getSubscribedTopics().contains(this.topic)) {
					subscriber.consume(TopicResponse.create(this.topic, data));
				} else {
					iterator.remove();
				}
			}
			
			if (subscribers.isEmpty()) {
				this.index.remove(userId);
			}
		}
	}
	
	protected DexDao dao;
	
	protected UserTopic(DexDao dao) {
		this.dao = dao;
	}
	
	@Override
	public void subscribe(Subscriber subscriber, TopicRequest request) {
		User user = this.parseUser(request);
		Index index = this.getIndex(request);
		if (index != null) {
			index.subscribe(subscriber, user.getId());
		}
	}
	
	private User parseUser(TopicRequest request) {
		Object rawAddress = request.getArguments() == null ? null : request.getArguments().get(KEY_ADDRESS);
		if (rawAddress == null) {
			throw BusinessException.validateFailed("user address not specified");
		}
		
		if (!(rawAddress instanceof String)) {
			throw BusinessException.validateFailed("user address is not a string");
		}
		
		String address = (String) rawAddress;
		Validators.validateAddress(address, AddressType.User, "address");
		
		return this.dao.getUserByName(address).mustGet();
	}
	
	protected abstract Index getIndex(TopicRequest request);
	
	@Override
	public void unsubscribe(Subscriber subscriber, TopicRequest request) {
		User user = this.parseUser(request);
		Index index = this.getIndex(request);
		if (index != null) {
			index.unsubscribe(subscriber, user.getId());
		}
	}

}
