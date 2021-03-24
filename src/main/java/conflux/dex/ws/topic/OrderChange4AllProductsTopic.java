package conflux.dex.ws.topic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conflux.dex.common.Handler;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.event.OrderEventArg;
import conflux.dex.model.Product;
import conflux.dex.ws.TopicRequest;

@Component
class OrderChange4AllProductsTopic extends UserTopic implements Handler<OrderEventArg> {
	
	private static final String TOPIC_NAME = "order.*";
	
	private Index index = new Index(TOPIC_NAME);
	
	private OrderChangeTopic topic;

	@Autowired
	public OrderChange4AllProductsTopic(DexDao dao, OrderChangeTopic topic) {
		super(dao);
		
		this.register(TOPIC_NAME);
		
		Events.PLACE_ORDER_SUBMITTED.addHandler(this);
		Events.CANCEL_ORDER_SUBMITTED.addHandler(this);
		Events.ORDER_STATUS_CHANGED.addHandler(data -> topic.onOrderStatusChanged(index, data));
		Events.ORDER_MATCHED.addHandler(data -> topic.onOrderMatched(index, data));
		
		this.topic = topic;
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
	public void handle(OrderEventArg data) {
		this.topic.publish(this.index, data.order);
	}

}
