package conflux.dex.common.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.junit.Assert;
import org.junit.Test;

public class SequentialWorkerTest extends CommonWorkerTest {
	
	private static class TestWorker extends SequentialWorker<TestWorkItem> {
		
		private List<String> results = new ArrayList<String>();

		public TestWorker(ExecutorService executor, String name) {
			super(executor, SequentialWorkerTest.class + " - " + name);
		}

		@Override
		protected void doWork(TestWorkItem data) throws Exception {
			if (data.retry <= 0) {
				this.results.add(String.valueOf(data.id));
			} else {
				this.results.add(data.id + "-" + data.retry);
				data.retry--;
				throw new Exception();
			}
		}
		
	}
	
	@Test(timeout = 1000)
	public void testRetryOnFailure() throws InterruptedException {
		TestWorker worker = new TestWorker(this.executor, "worker - 1");
		
		worker.submit(new TestWorkItem(1, 2));
		worker.submit(new TestWorkItem(2, 0));
		worker.submit(new TestWorkItem(3, 1));
		
		this.waitForCompletion(worker);
		
		Assert.assertEquals("[1-2, 1-1, 1, 2, 3-1, 3]", worker.results.toString());
	}

}
