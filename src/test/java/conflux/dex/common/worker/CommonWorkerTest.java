package conflux.dex.common.worker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;

public class CommonWorkerTest {
	
	public ExecutorService executor;
	
	@Before
	public void setup() {
		executor = Executors.newCachedThreadPool();
	}
	
	@After
	public void cleanup() throws InterruptedException {
		executor.shutdownNow();
		executor.awaitTermination(1, TimeUnit.SECONDS);
	}
	
	public void waitForCompletion(AsyncWorker<?> worker) throws InterruptedException {
		while (worker.isWorking()) {
			Thread.sleep(5);
		}
	}
	
}

class TestWorkItem {
	
	public int id;
	public int retry;
	
	public TestWorkItem(int id) {
		this(id, 0);
	}
	
	public TestWorkItem(int id, int retry) {
		this.id = id;
		this.retry = retry;
	}
	
}