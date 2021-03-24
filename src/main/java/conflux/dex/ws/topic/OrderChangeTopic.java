package conflux.dex.ws.topic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import conflux.dex.model.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conflux.dex.common.Handler;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.event.OrderEventArg;
import conflux.dex.model.Order;
import conflux.dex.model.OrderChange;
import conflux.dex.model.Product;
import conflux.dex.worker.TradeDetails;
import conflux.dex.ws.TopicRequest;

@Component
class OrderChangeTopic extends UserTopic implements Handler<OrderEventArg> {
	
	private ConcurrentMap<String, Index> topicIndex = new ConcurrentHashMap<String, Index>();
	private ConcurrentMap<Integer, Index> productIndex = new ConcurrentHashMap<Integer, Index>();

	@Autowired
	public OrderChangeTopic(DexDao dao) {
		super(dao);
		
		Events.PLACE_ORDER_SUBMITTED.addHandler(this);
		Events.CANCEL_ORDER_SUBMITTED.addHandler(this);
		Events.ORDER_STATUS_CHANGED.addHandler(data -> {
			Index index = productIndex.get(data.getProductId());
			onOrderStatusChanged(index, data);
		});
		Events.ORDER_MATCHED.addHandler(data -> {
			Index index = productIndex.get(data.getTrade().getProductId());
			onOrderMatched(index, data);
		});
	}

	@Override
	protected void register(Product product) {
		String topic = String.format("order.%s", product.getName());
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
	public void handle(OrderEventArg data) {
		Index index = this.productIndex.get(data.order.getProductId());
		this.publish(index, data.order);
	}
	
	public void publish(Index index, Order order) {
		if (index != null && index.isSubscribed(order.getUserId())) {
			OrderChange change = OrderChange.orderStatusChanged(order);
			index.publish(order.getUserId(), change);
		}
	}
	
	public void onOrderStatusChanged(Index index, conflux.dex.matching.Order data) {
		if (index != null && index.isSubscribed(data.getUserId())) {
			Order order = this.dao.mustGetOrder(data.getId());
			OrderChange change = OrderChange.orderStatusChanged(order);
			index.publish(data.getUserId(), change);
		}
	}
	
	public void onOrderMatched(Index index, TradeDetails data) {
		if (index == null) {
			return;
		}

		pubMatch(index, data, data.getTakerOrder(), true);
		pubMatch(index, data, data.getMakerOrder(), false);
	}

	private void pubMatch(Index index, TradeDetails data, conflux.dex.matching.Order mOrder, boolean taker) {
		if (index.isSubscribed(mOrder.getUserId())) {
			Order order = this.dao.mustGetOrder(mOrder.getId());
			OrderChange change = OrderChange.orderMatched(data.getTrade(), order, taker);
			change.setCompleted(mOrder.isCompleted());
			if (!mOrder.isCompleted()) {
				// fix fields, under middle status, we can't use data in DB.
				change.setStatus(OrderStatus.Open);
				change.setFilledAmount(mOrder.getFilledAmount());
				change.setFilledFunds(mOrder.getFilledFunds());
			}
			index.publish(mOrder.getUserId(), change);
		}
	}

}
