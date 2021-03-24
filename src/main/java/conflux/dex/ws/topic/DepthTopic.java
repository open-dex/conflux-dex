package conflux.dex.ws.topic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import conflux.dex.model.Product;
import conflux.dex.worker.DepthAggregateManager;

@Component
class DepthTopic extends MarketTopic {
	
	// map<productId, map<step, topic>>
	private ConcurrentMap<Integer, ConcurrentMap<Integer, String>> index = new ConcurrentHashMap<Integer, ConcurrentMap<Integer, String>>();
	
	@Override
	protected void register(Product product) {
		int productId = product.getId();
		this.index.put(productId, new ConcurrentHashMap<Integer, String>());
		
		for (int step : DepthAggregateManager.DefaultSteps) {
			String topic = String.format("market.%s.depth.step%s", product.getName(), step);
			this.register(topic);
			this.index.get(productId).put(step, topic);
		}
	}
	
	@Scheduled(initialDelay = 1000, fixedDelay = 1000)
	public void publish() {
		if (this.engineService == null) {
			return;
		}
		
		for (Map.Entry<Integer, DepthAggregateManager> entry : this.engineService.depthAggs.entrySet()) {
			for (int step : DepthAggregateManager.DefaultSteps) {
				this.publish(entry.getKey(), step, entry.getValue());
			}
		}
	}
	
	private void publish(int productId, int step, DepthAggregateManager manager) {
		ConcurrentMap<Integer, String> stepIndex = this.index.get(productId);
		if (stepIndex == null) {
			return;
		}
		
		String topic = stepIndex.get(step);
		if (topic == null || !this.isSubscribed(topic)) {
			return;
		}
		
		int depth = step == 0 ? 150 : 20;
		Object data = manager.getLevels(step, depth);
		this.publish(topic, data);
	}

}
