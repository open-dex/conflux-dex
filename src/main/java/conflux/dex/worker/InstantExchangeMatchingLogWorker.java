package conflux.dex.worker;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.codahale.metrics.Timer;

import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.QueueMetric;
import conflux.dex.common.worker.SequentialWorker;
import conflux.dex.matching.InstantExchangeLog;
import conflux.dex.matching.InstantExchangeLogHandler;
import conflux.dex.model.Product;

/**
 * Handle instant exchange order matching logs in sequence.
 */
public class InstantExchangeMatchingLogWorker extends SequentialWorker<InstantExchangeLog> {
	
	private static QueueMetric handleQueue = Metrics.queue(InstantExchangeMatchingLogWorker.class);
	private static Timer handlePerf = Metrics.timer(InstantExchangeMatchingLogWorker.class, "perf");

	private List<InstantExchangeLogHandler> handlers = new LinkedList<InstantExchangeLogHandler>();

	public final InstantExchangeTradeSettlement tradeSettlement;

	public InstantExchangeMatchingLogWorker(ExecutorService executor, InstantExchangeTradeSettlement tradeSettlement, Product product) {
		super(executor, "InstantExchangeMatchingLogWorker-" + product.getName());
		
		this.setQueueMetric(handleQueue);
		this.setHandleDataPerf(handlePerf);
		
		this.tradeSettlement = tradeSettlement;
		this.handlers.add(tradeSettlement);
	}

	@Override
	protected void doWork(InstantExchangeLog data) throws Exception {
		for (InstantExchangeLogHandler handler : handlers)
			handler.handle(data);
	}
	
	public void addHandler(InstantExchangeLogHandler handler) {
		this.handlers.add(handler);
	}
}
