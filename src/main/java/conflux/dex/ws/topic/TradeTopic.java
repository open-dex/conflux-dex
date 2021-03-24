package conflux.dex.ws.topic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import conflux.dex.common.Handler;
import conflux.dex.event.Events;
import conflux.dex.model.Product;
import conflux.dex.worker.TradeDetails;

@Component
class TradeTopic extends MarketTopic implements Handler<TradeDetails> {
	
	private ConcurrentMap<Integer, String> index = new ConcurrentHashMap<Integer, String>();
	
	public TradeTopic() {
		Events.ORDER_MATCHED.addHandler(this);
	}
	
	@Override
	protected void register(Product product) {
		String topic = String.format("market.%s.trade.detail", product.getName());
		this.register(topic);
		this.index.put(product.getId(), topic);
	}

	@Override
	public void handle(TradeDetails data) {
		String topic = this.index.get(data.getTrade().getProductId());
		this.publish(topic, data.getTrade());
	}

}
