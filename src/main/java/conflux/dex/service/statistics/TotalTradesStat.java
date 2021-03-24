package conflux.dex.service.statistics;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.codahale.metrics.Counter;

import conflux.dex.common.Handler;
import conflux.dex.common.Metrics;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.Product;
import conflux.dex.worker.TradeDetails;
import conflux.dex.worker.ticker.DefaultTickGranularity;

@Component
public class TotalTradesStat implements Handler<TradeDetails> {
	
	private Counter totalTradesCounter = Metrics.counter(TotalTradesStat.class);
	
	public TotalTradesStat() {
		Events.ORDER_MATCHED.addHandler(this);
	}

	@Override
	public void handle(TradeDetails data) {
		this.totalTradesCounter.inc();
	}
	
	@Autowired
	public void init(DexDao dao) {
		List<Product> products = dao.listProducts();
		
		for (Product product : products) {
			long count = dao.getTradeCount(product.getId(), DefaultTickGranularity.Month.getValue());
			this.totalTradesCounter.inc(count);
		}
	}
	
	public long getValue() {
		return this.totalTradesCounter.getCount();
	}

}
