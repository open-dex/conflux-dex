package conflux.dex.ws.topic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conflux.dex.common.Handler;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.Product;
import conflux.dex.model.UserTrade;
import conflux.dex.worker.TradeDetails;
import conflux.dex.ws.TopicRequest;

@Component
class UserTradeTopic extends UserTopic implements Handler<TradeDetails> {
	
	private ConcurrentMap<String, Index> topicIndex = new ConcurrentHashMap<String, Index>();
	private ConcurrentMap<Integer, Index> productIndex = new ConcurrentHashMap<Integer, Index>();
	
	@Autowired
	public UserTradeTopic(DexDao dao) {
		super(dao);
		
		Events.ORDER_MATCHED.addHandler(this);
	}
	
	@Override
	protected void register(Product product) {
		String topic = String.format("trade.%s", product.getName());
		this.register(topic);
		
		Index index = new Index(topic);
		this.topicIndex.put(topic, index);
		this.productIndex.put(product.getId(), index);
	}
	
	@Override
	protected Index getIndex(TopicRequest request) {
		return this.topicIndex.get(request.getTopic());
	}
	
	@Override
	public void handle(TradeDetails data) {
		Index index = this.productIndex.get(data.getTrade().getProductId());
		this.publish(index, data);
	}
	
	public void publish(Index index, TradeDetails data) {
		if (index == null) {
			return;
		}
		
		if (index.isSubscribed(data.getTakerOrder().getUserId())) {
			UserTrade ut = UserTrade.create(data.getTrade(), data.getTakerOrder().getType(), true);
			index.publish(data.getTakerOrder().getUserId(), ut);
		}
		
		if (index.isSubscribed(data.getMakerOrder().getUserId())) {
			UserTrade ut = UserTrade.create(data.getTrade(), data.getMakerOrder().getType(), false);
			index.publish(data.getMakerOrder().getUserId(), ut);
		}
	}

}
