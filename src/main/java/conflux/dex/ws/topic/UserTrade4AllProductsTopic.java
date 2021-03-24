package conflux.dex.ws.topic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conflux.dex.common.Handler;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.Product;
import conflux.dex.worker.TradeDetails;
import conflux.dex.ws.TopicRequest;

@Component
class UserTrade4AllProductsTopic extends UserTopic implements Handler<TradeDetails> {
	
	private static final String TOPIC_NAME = "trade.*";
	
	private Index index = new Index(TOPIC_NAME);
	
	private UserTradeTopic topic;
	
	@Autowired
	public UserTrade4AllProductsTopic(DexDao dao, UserTradeTopic topic) {
		super(dao);
		
		this.register(TOPIC_NAME);
		
		this.topic = topic;
		
		Events.ORDER_MATCHED.addHandler(this);
	}

	@Override
	protected void register(Product product) {
		// do nothing
	}
	
	@Override
	protected Index getIndex(TopicRequest request) {
		return this.index;
	}
	
	@Override
	public void handle(TradeDetails data) {
		this.topic.publish(this.index, data);
	}

}
