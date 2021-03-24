package conflux.dex.ws.topic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import conflux.dex.model.Product;
import conflux.dex.model.Tick;
import conflux.dex.worker.ticker.Ticker;

@Component
class TickLast24HoursTopic extends MarketTopic {
	
	private ConcurrentMap<Integer, String> index = new ConcurrentHashMap<Integer, String>();
	private ConcurrentMap<Integer, Tick> lastTicks = new ConcurrentHashMap<Integer, Tick>();

	@Override
	protected void register(Product product) {
		String topic = String.format("market.%s.detail", product.getName());
		this.register(topic);
		this.index.put(product.getId(), topic);
	}
	
	@Scheduled(initialDelay = 100, fixedDelay = 100)
	public void publishLast24HoursTick() {
		if (this.engineService == null) {
			return;
		}
		
		for (Map.Entry<Integer, Ticker> entry : this.engineService.tickers.entrySet()) {
			this.publish(entry.getKey(), entry.getValue());
		}
	}
	
	private void publish(int productId, Ticker ticker) {
		String topic = this.index.getOrDefault(productId, "");
		if (!this.isSubscribed(topic)) {
			return;
		}
		
		Tick tick = ticker.getLast24HoursTick();
		if (tick == null) {
			return;
		}
		
		Tick lastTick = this.lastTicks.get(productId);
		if (lastTick != null && !tick.isChanged(lastTick)) {
			return;
		}
		
		this.lastTicks.put(productId, tick);
		
		this.publish(topic, tick);
	}

}
