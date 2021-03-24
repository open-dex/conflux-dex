package conflux.dex.ws.topic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import conflux.dex.common.Handler;
import conflux.dex.event.Events;
import conflux.dex.model.BestBidOffer;
import conflux.dex.model.Product;

/**
 * Best bid/offer topic
 */
@Component
class BboTopic extends MarketTopic implements Handler<BestBidOffer> {
	
	private ConcurrentMap<String, String> index = new ConcurrentHashMap<String, String>();
	
	public BboTopic() {
		Events.BBO_CHANGED.addHandler(this);
	}
	
	@Override
	protected void register(Product product) {
		String topic = String.format("market.%s.bbo", product.getName());
		this.register(topic);
		this.index.put(product.getName(), topic);
	}

	@Override
	public void handle(BestBidOffer data) {
		String topic = this.index.get(data.getProduct());
		this.publish(topic, data);
	}

}
