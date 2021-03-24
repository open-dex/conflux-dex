package conflux.dex.common.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;

import org.junit.Assert;
import org.junit.Test;

public class AsyncWorkerTest extends CommonWorkerTest {
	
	private static class TestWorker extends AsyncWorker<TestWorkItem> {
		
		private List<Integer> nums = new ArrayList<Integer>();
		private long workTimeMillis;

		public TestWorker(ExecutorService executor, String name, long workTimeMillis) {
			super(executor, AsyncWorkerTest.class + " - " + name);
			
			this.workTimeMillis = workTimeMillis;
		}

		@Override
		protected int doWork(ConcurrentLinkedDeque<TestWorkItem> queue) {
			try {
				Thread.sleep(this.workTimeMillis);
			} catch (InterruptedException e) {
				return 0;
			}
			
			this.nums.add(queue.removeFirst().id);
			
			return 1;
		}
		
	}
	
	@Test(timeout = 1000)
	public void testIsWorking() throws InterruptedException {
		// not working
		TestWorker worker = new TestWorker(this.executor, "worker - 1", 30);
		Assert.assertFalse(worker.isWorking());
		
		// working
		worker.submit(new TestWorkItem(1));
		Assert.assertTrue(worker.isWorking());
		
		// wait for completion of all tasks
		while (worker.nums.isEmpty()) {
			Thread.sleep(5);
		}
		
		// not working again
		Assert.assertFalse(worker.isWorking());
	}
	
	@Test(timeout = 1000)
	public void testSubmit() throws InterruptedException {
		TestWorker worker = new TestWorker(this.executor, "worker - 2", 1);
		worker.submit(new TestWorkItem(1));
		worker.submit(new TestWorkItem(3));
		worker.submit(new TestWorkItem(2));
		
		this.waitForCompletion(worker);
		
		Assert.assertEquals("[1, 3, 2]", worker.nums.toString());
	}
	
	@Test(timeout = 1000)
	public void testSubmitAsFirst() throws InterruptedException {
		TestWorker worker = new TestWorker(this.executor, "worker - 3", 1);
		worker.submit(new TestWorkItem(1), true);
		worker.submit(new TestWorkItem(3), true);
		worker.submit(new TestWorkItem(2), true);
		
		this.waitForCompletion(worker);
		
		Assert.assertEquals("[2, 3, 1]", worker.nums.toString());
	}

}
