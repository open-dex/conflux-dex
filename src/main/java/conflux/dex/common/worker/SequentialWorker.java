package conflux.dex.common.worker;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asynchronous worker to handle data in sequence.
 */
public abstract class SequentialWorker<T> extends AsyncWorker<T> {
	
	private AtomicReference<T> handlingData = new AtomicReference<T>();
	
	protected SequentialWorker(ExecutorService executor, String name) {
		super(executor, name);
	}
	
	public T getHandlingData() {
		return this.handlingData.get();
	}
	
	protected abstract void doWork(T data) throws Exception;
	
	@Override
	protected int doWork(ConcurrentLinkedDeque<T> queue) {
		if (queue.isEmpty()) {
			return 0;
		}
		
		T data = queue.removeFirst();
		this.handlingData.set(data);
		
		try {
			this.doWork(data);
			this.onSucceeded();
			return 1;
		} catch (Exception e) {
			this.onFailed(e, data);
			// execute the failed task again until succeeded.
			queue.addFirst(data);
			return 0;
		} finally {
			this.handlingData.set(null);
		}
	}
	
}
