package conflux.dex.ws.topic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import conflux.dex.common.Handler;
import conflux.dex.event.Events;
import conflux.dex.model.Product;
import conflux.dex.model.Tick;
import conflux.dex.worker.ticker.TickGranularity;
import conflux.dex.worker.ticker.Ticker;

@Component
class TickTopic extends MarketTopic implements Handler<Tick> {
	
	// map<productId, map<granularity, topic>>
	private ConcurrentMap<Integer, ConcurrentMap<Integer, String>> index = new ConcurrentHashMap<Integer, ConcurrentMap<Integer, String>>();
	
	public TickTopic() {
		Events.TICK_CHANGED.addHandler(this);
	}

	@Override
	protected void register(Product product) {
		int productId = product.getId();
		this.index.put(productId, new ConcurrentHashMap<Integer, String>());
		
		for (TickGranularity granularity : Ticker.DEFAULT_GRANULARITIES) {
			String topic = String.format("market.%s.tick.%s", product.getName(), granularity.getName());
			this.register(topic);
			this.index.get(productId).put(granularity.getValue(), topic);
		}
	}

	@Override
	public void handle(Tick data) {
		ConcurrentMap<Integer, String> granularityIndex = this.index.get(data.getProductId());
		if (granularityIndex == null) {
			return;
		}
		
		String topic = granularityIndex.get(data.getGranularity());
		this.publish(topic, data);
	}

}
