package conflux.dex.worker.batch;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Histogram;

import conflux.dex.common.Event;
import conflux.dex.common.Handler;
import conflux.dex.common.Metrics;
import conflux.dex.common.worker.AsyncWorker;
import conflux.dex.event.Events;
import conflux.dex.matching.Log;
import conflux.dex.matching.LogType;
import conflux.dex.model.Product;

/**
 * Sequentially handle order matching logs in batch.
 * 
 * Compared to handle order matching log one by one, handling in batch will help to 
 * improve the performance of database operations in following ways:
 * 1) Reduce the number of database transactions, which is time consuming in case of high trade TPS.
 * 2) Aggregate balance changes for the same user.
 * 3) Aggregate tick changes and update only once.
 */
public class MatchingLogBatchWorker extends AsyncWorker<Log> {
	
	private static Logger logger = LoggerFactory.getLogger(MatchingLogBatchWorker.class);
	
	private static Histogram statBatchTotal = Metrics.histogram(MatchingLogBatchWorker.class, "batch", "total");
	private static Histogram statBatchTrades = Metrics.histogram(MatchingLogBatchWorker.class, "batch", "trades");
	
	private int batchSize;
	private int batchSizeMax;
	private Event<List<Log>> event = new Event<List<Log>>();

	public MatchingLogBatchWorker(ExecutorService executor, Product product, int batchSize, int batchSizeMax) {
		super(executor, "MatchingLogBatchWorker-" + product.getName());
		
		if (batchSize > batchSizeMax) {
			throw new IllegalArgumentException("batchSize should be <= batchSizeMax");
		}
		
		this.batchSize = batchSize;
		this.batchSizeMax = batchSizeMax;
		
		this.setQueueMetric(Metrics.queue(MatchingLogBatchWorker.class));
		this.setHandleDataPerf(Metrics.timer(MatchingLogBatchWorker.class, "perf"));
	}
	
	@Override
	protected int doWork(ConcurrentLinkedDeque<Log> queue) {
		LinkedList<Log> logs = getLogs(queue, this.batchSize, this.batchSizeMax);
		statBatchTotal.update(logs.size());
		statBatchTrades.update(logs.stream().filter(l -> l.getType() == LogType.OrderMatched).count());
		
		try {
			this.event.fire(logs);
			return logs.size();
		} catch (Exception e) {
			logger.error("failed to handle matching logs in batch", e);
			
			while (!logs.isEmpty()) {
				queue.addFirst(logs.removeLast());
			}

			Events.WORKER_ERROR.fire(String.format("failed to handle matching logs in batch: %s", e.getMessage()));
			
			return 0;
		}
	}
	
	static LinkedList<Log> getLogs(ConcurrentLinkedDeque<Log> queue, int batchSize, int batchSizeMax) {
		LinkedList<Log> logs = new LinkedList<Log>();
		
		for (int i = 0; i < batchSize && !queue.isEmpty(); i++) {
			logs.add(queue.removeFirst());
		}
		
		// handle more logs in the same match
		while (!isSealed(logs.getLast()) && logs.size() < batchSizeMax && !queue.isEmpty()) {
			logs.add(queue.removeFirst());
		}
		
		return logs;
	}
	
	private static boolean isSealed(Log log) {
		switch (log.getType()) {
		case OrderMatched:
		case MakerOrderCompleted:
			return false;
		default:
			return true;
		}
	}
	
	public void addBatchHandler(Handler<List<Log>> handler) {
		this.event.addHandler(handler);
	}
	
	public void addHandler(Handler<Log> handler) {
		this.event.addHandler(logs -> {
			for (Log log : logs) {
				handler.handle(log);
			}
		});
	}

}
