package conflux.dex.common.worker;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous worker to handle data in batch.
 */
public abstract class BatchWorker<T extends BatchWorker.Batchable> extends AsyncWorker<T> {
	
	private static final Logger logger = LoggerFactory.getLogger(BatchWorker.class);
	
	public static interface Batchable extends WorkItem {
		boolean isBatchable();
		boolean batchWith(Batchable other);
		int size();
	}
	
	private int batchSize;
	private long waitForNextTimeoutMillis;
	private AtomicReference<T> handlingData = new AtomicReference<T>();

	protected BatchWorker(ExecutorService executor, String name, int batchSize, long waitForBatchTimeoutMillis) {
		super(executor, name);
		
		this.batchSize = batchSize;
		this.waitForNextTimeoutMillis = batchSize == 0 ? 0 : waitForBatchTimeoutMillis / batchSize;
	}
	
	public int getBatchSize() {
		return batchSize;
	}
	
	public long getWaitForNextTimeoutMillis() {
		return waitForNextTimeoutMillis;
	}
	
	public T getHandlingData() {
		return this.handlingData.get();
	}
	
	protected abstract void doWork(T batch) throws Exception;
	
	@Override
	public void submit(T data, boolean asFirst) {
		super.submit(data, asFirst);
		
		if (data.size() > 1) {
			this.queueMetric.enqueue(data.size() - 1);
		}
	}

	@Override
	protected int doWork(ConcurrentLinkedDeque<T> queue) {
		if (queue.isEmpty()) {
			return 0;
		}
		
		T batch;
		
		try {
			batch = this.popBatch(queue);
		} catch (InterruptedException e) {
			logger.error("failed to do work, interrupted when pop batch", e);
			return 0;
		}
		
		this.handlingData.set(batch);
		
		try {
			this.doWork(batch);
			this.onSucceeded();
			return batch.size();
		} catch (InterruptedException e) {
			// server shutdown.
			return 0;
		} catch (Exception e) {
			this.onFailed(e, batch);
			// execute the failed task again until succeeded.
			queue.addFirst(batch);
			return 0;
		} finally {
			this.handlingData.set(null);
		}
	}
	
	private T popBatch(ConcurrentLinkedDeque<T> queue) throws InterruptedException {
		T item = queue.removeFirst();
		
		if (!item.isBatchable()) {
			return item;
		}
		
		int retry = this.batchSize;
		
		for (int i = item.size(); i < this.batchSize; i++) {
			if (queue.isEmpty()) {
				if (this.waitForNextTimeoutMillis <= 0) {
					break;
				}
				
				while (retry > 0 && queue.isEmpty()) {
					Thread.sleep(this.waitForNextTimeoutMillis);
					retry--;
				}
				
				if (queue.isEmpty()) {
					break;
				}
			}
			
			if (item.batchWith(queue.getFirst())) {
				queue.removeFirst();
			} else {
				break;
			}
		}
		
		return item;
	}

}
