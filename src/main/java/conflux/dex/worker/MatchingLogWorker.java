package conflux.dex.worker;

import java.util.concurrent.ExecutorService;

import com.codahale.metrics.Timer;

import conflux.dex.common.Event;
import conflux.dex.common.Handler;
import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.QueueMetric;
import conflux.dex.common.worker.SequentialWorker;
import conflux.dex.matching.Log;
import conflux.dex.model.Product;

/**
 * Handle order matching logs in sequence.
 */
public class MatchingLogWorker extends SequentialWorker<Log> {
	
	private static QueueMetric handleQueue = Metrics.queue(MatchingLogWorker.class);
	private static Timer handlePerf = Metrics.timer(MatchingLogWorker.class, "perf");
	
	private Event<Log> event = new Event<Log>();

	public MatchingLogWorker(ExecutorService executor, Product product) {
		super(executor, "MatchingLogWorker-" + product.getName());
		
		this.setQueueMetric(handleQueue);
		this.setHandleDataPerf(handlePerf);
	}

	@Override
	protected void doWork(Log data) throws Exception {
		this.event.fire(data);
	}
	
	public void addHandler(Handler<Log> handler) {
		this.event.addHandler(handler);
	}
}
