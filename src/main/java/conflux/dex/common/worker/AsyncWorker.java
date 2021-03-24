package conflux.dex.common.worker;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import conflux.dex.common.BusinessFault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Metrics.QueueMetric;
import conflux.dex.common.Utils;

/**
 * Worker to handle data asynchronously.
 * 
 * @param <T> data to handle.
 */
public abstract class AsyncWorker<T> implements Runnable {
	
	public static interface WorkItem {
		String toShortDisplay();
		String toLongDisplay();
	}
	
	private static final Logger logger = LoggerFactory.getLogger(AsyncWorker.class);
	
	private ExecutorService executor;
	private String name;
	
	private ConcurrentLinkedDeque<T> queue = new ConcurrentLinkedDeque<T>();
	private AtomicBoolean working = new AtomicBoolean();
	private Object lock = new Object();
	
	private AtomicLong errorCounter = new AtomicLong();
	
	protected QueueMetric queueMetric;
	private Timer handleDataPerf;
	
	protected AsyncWorker(ExecutorService executor, String name) {
		this.executor = executor;
		this.name = name;
	}

	/**
	 * Adaptor to access queue, template could just be new RealType[0].
	 * It's impossible to write queue.toArray(new T[0]).
	 * @param template
	 * @return
	 */
	protected T[] getTasks(T[] template) {
		return queue.toArray(template);
	}
	
	public String getName() {
		return name;
	}
	
	public int getPendingCount() {
		return this.queue.size();
	}
	
	public boolean isWorking() {
		return this.working.get();
	}
	
	public void setQueueMetric(QueueMetric queueMetric) {
		this.queueMetric = queueMetric;
	}
	
	public void setHandleDataPerf(Timer handleDataPerf) {
		this.handleDataPerf = handleDataPerf;
	}
	
	public void submit(T data) {
		this.submit(data, false);
	}
	
	public void submit(T data, boolean asFirst) {
		synchronized (this.lock) {
			if (asFirst) {
				this.queue.addFirst(data);
			} else {
				this.queue.addLast(data);
			}
		}
		
		if (this.queueMetric != null) {
			this.queueMetric.enqueue();
		}
		
		if (this.working.compareAndSet(false, true)) {
			this.executor.submit(this);
		}
	}
	
	/**
	 * Pop data from queue to handle, and return the number of handled data.
	 */
	protected abstract int doWork(ConcurrentLinkedDeque<T> queue);
	
	@Override
	public void run() {
		long start = this.handleDataPerf == null ? 0 : System.currentTimeMillis();
		
		int handledCount = this.doWork(this.queue);
		
		if (this.handleDataPerf != null) {
			this.handleDataPerf.update(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
		}
		
		if (this.queueMetric != null) {
			if (handledCount > 0) {
				this.queueMetric.dequeue(handledCount);
			} else if (handledCount < 0) {
				this.queueMetric.enqueue(-handledCount);
			}
		}
		
		boolean completed;
		synchronized (this.lock) {
			completed = this.queue.isEmpty();
			if (completed) {
				this.working.set(false);
			}
		}
		
		if (!completed) {
			this.executor.submit(this);
		}
	}
	
	protected void onSucceeded() {
		this.errorCounter.set(0);
		WorkerError.clear(this);
	}
	
	protected void onFailed(Exception e, T data) {
		if ( e instanceof BusinessException
			&& ((BusinessException) e).getCode() == BusinessFault.SystemPaused.getCode()) {
			return;
		}
		
		long retry = this.errorCounter.getAndIncrement();
		
		if (retry == 0) {
			logger.warn(this.toLog(data, true, e), e);
		} else if (retry < 3) {
			logger.warn(this.toLog(data, false, e), e);
		} else if (retry < 5) {
			logger.error(this.toLog(data, false, e), e);
		} else if (logger.isDebugEnabled()) {
			// do not output too many logs for unrecoverable errors.
			logger.debug(this.toLog(data, false, e), e);
		}
		
		WorkerError.update(this, this.toLog(data, true), e, retry);
	}
	
	private String toLog(T data, boolean longDisplay, Exception e) {
		return String.format("failed to do work, worker type = %s, error message = %s, data = %s",
				this.getClass().getSimpleName(), e.getMessage(), this.toLog(data, longDisplay));
	}
	
	private String toLog(T data, boolean longDisplay) {
		if (data == null) {
			return "<null>";
		}
		
		if (!(data instanceof WorkItem)) {
			return String.format("%s%s", data.getClass().getSimpleName(), Utils.toJson(data));
		}
		
		WorkItem item = (WorkItem) data;
		
		return longDisplay ? item.toLongDisplay() : item.toShortDisplay();
	}
}
