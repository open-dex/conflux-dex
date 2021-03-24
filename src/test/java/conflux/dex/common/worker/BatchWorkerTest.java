package conflux.dex.common.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.junit.Assert;
import org.junit.Test;

import conflux.dex.common.worker.BatchWorker.Batchable;

public class BatchWorkerTest extends CommonWorkerTest {
	
	private static class Trade implements BatchWorker.Batchable {
		
		private List<Integer> ids = new ArrayList<Integer>();
		private int retry;
		
		public Trade(int id, int retry) {
			this.ids.add(id);
			this.retry = retry;
		}
		
		@Override
		public boolean isBatchable() {
			return true;
		}

		@Override
		public boolean batchWith(Batchable other) {
			if (!(other instanceof Trade)) {
				return false;
			}
			
			this.ids.addAll(((Trade) other).ids);
			
			return true;
		}

		@Override
		public int size() {
			return this.ids.size();
		}

		@Override
		public String toShortDisplay() {
			return this.ids.toString();
		}

		@Override
		public String toLongDisplay() {
			return this.ids.toString();
		}
		
	}
	
	private static class TestWorker extends BatchWorker<Trade> {
		
		private List<List<Integer>> result = new ArrayList<List<Integer>>();
		private int workTimeMillis;

		public TestWorker(ExecutorService executor, String name, int batchSize, int workTimeMillis, long waitForBatchTimeoutMillis) {
			super(executor, BatchWorkerTest.class + " - " + name, batchSize, waitForBatchTimeoutMillis);
			
			this.workTimeMillis = workTimeMillis;
		}

		@Override
		protected void doWork(Trade batch) throws Exception {
			Thread.sleep(this.workTimeMillis);
			
			if (batch.retry == 0) {
				this.result.add(batch.ids);
			} else {
				batch.retry--;
				throw new Exception("Test Exception, don't care.");
			}
		}
		
	}
	
	@Test(timeout = 1000)
	public void testBatchItem() throws InterruptedException {
		TestWorker worker = new TestWorker(this.executor, "worker - 1", 3, 10, 0);
		
		worker.submit(new Trade(1, 0));
		
		Thread.sleep(5);
		worker.submit(new Trade(2, 0));
		worker.submit(new Trade(3, 0));
		worker.submit(new Trade(4, 0));
		worker.submit(new Trade(5, 0));
		
		this.waitForCompletion(worker);
		
		Assert.assertEquals("[[1], [2, 3, 4], [5]]", worker.result.toString());
	}
	
	@Test(timeout = 1000)
	public void testRetryWithMore() throws InterruptedException {
		TestWorker worker = new TestWorker(this.executor, "worker - 2", 3, 10, 0);
		
		worker.submit(new Trade(1, 1));
		
		Thread.sleep(5);
		worker.submit(new Trade(2, 0));
		worker.submit(new Trade(3, 0));
		worker.submit(new Trade(4, 0));
		worker.submit(new Trade(5, 0));
		
		this.waitForCompletion(worker);
		
		Assert.assertEquals("[[1, 2, 3], [4, 5]]", worker.result.toString());
	}
	
	@Test(timeout = 1000)
	public void testRetryWithMore2() throws InterruptedException {
		TestWorker worker = new TestWorker(this.executor, "worker - 3", 3, 10, 0);
		
		worker.submit(new Trade(1, 0));
		
		Thread.sleep(5);
		worker.submit(new Trade(2, 1));
		worker.submit(new Trade(3, 0));
		
		Thread.sleep(10);
		worker.submit(new Trade(4, 0));
		worker.submit(new Trade(5, 0));
		
		this.waitForCompletion(worker);
		
		Assert.assertEquals("[[1], [2, 3, 4], [5]]", worker.result.toString());
	}
	
	@Test(timeout = 1000)
	public void testBatchMoreTimeout() throws InterruptedException {
		TestWorker worker = new TestWorker(this.executor, "worker - 4", 3, 10, 15);
		
		// wait for more to handle in batch, but only wait for 15 ms
		worker.submit(new Trade(1, 0));
		
		// trade 2 will be batched with trade 1
		Thread.sleep(10);
		worker.submit(new Trade(2, 0));
		
		// trade 3 will not be batched with trade 1 & 2, since 15 ms timeout.
		Thread.sleep(10);
		worker.submit(new Trade(3, 0));
		
		this.waitForCompletion(worker);
		
		Assert.assertEquals("[[1, 2], [3]]", worker.result.toString());
	}


}
