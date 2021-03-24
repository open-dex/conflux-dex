package conflux.dex.ws;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;

import conflux.dex.common.BusinessException;
import conflux.dex.event.Events;
import conflux.dex.model.Product;
import conflux.dex.service.EngineService;

public abstract class Topic {
	
	private static final ConcurrentMap<String, Topic> topics = new ConcurrentHashMap<String, Topic>();
	
	protected EngineService engineService;
	
	protected void register(String index) {
		topics.put(index, this);
	}
	
	public static Topic indexOf(String index) throws BusinessException {
		return topics.get(index);
	}
	
	@Autowired
	public void setEngineService(EngineService service) {
		this.engineService = service;
		
		for (Product product : service.getPreloadedProducts()) {
			this.register(product);
		}
		
		Events.NEW_PRODUCT_ADDED.addHandler(product -> Topic.this.register(product));
	}
	
	protected abstract void register(Product product);
	
	public abstract void subscribe(Subscriber subscriber, TopicRequest request);
	public abstract void unsubscribe(Subscriber subscriber, TopicRequest request);
	
}
