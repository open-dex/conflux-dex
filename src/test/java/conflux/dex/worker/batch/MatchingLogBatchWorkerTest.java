package conflux.dex.worker.batch;

import java.util.concurrent.ConcurrentLinkedDeque;

import org.junit.Assert;
import org.junit.Test;

import conflux.dex.matching.Log;

public class MatchingLogBatchWorkerTest {
	
	@Test
	public void testBatchNotFull() {
		ConcurrentLinkedDeque<Log> logs = new ConcurrentLinkedDeque<Log>();
		
		logs.add(Log.newMatchLog(1, null, null, null));
		logs.add(Log.newCompleteLog(1, null, false));
		logs.add(Log.newCompleteLog(1, null, true));
		
		Assert.assertEquals(3, MatchingLogBatchWorker.getLogs(logs, 10, 20).size());
		Assert.assertTrue(logs.isEmpty());
	}
	
	@Test
	public void testBatchFullSealed() {
		ConcurrentLinkedDeque<Log> logs = new ConcurrentLinkedDeque<Log>();
		
		logs.add(Log.newMatchLog(1, null, null, null));
		logs.add(Log.newCompleteLog(1, null, false));
		logs.add(Log.newCompleteLog(1, null, true));
		
		logs.add(Log.newCompleteLog(1, null, false));
		
		Assert.assertEquals(3, MatchingLogBatchWorker.getLogs(logs, 3, 10).size());
		Assert.assertEquals(1, logs.size());
	}
	
	@Test
	public void testBatchFullNotSealed() {
		ConcurrentLinkedDeque<Log> logs = new ConcurrentLinkedDeque<Log>();
		
		logs.add(Log.newMatchLog(1, null, null, null));
		logs.add(Log.newCompleteLog(1, null, false));
		logs.add(Log.newCompleteLog(1, null, true));
		
		logs.add(Log.newMatchLog(1, null, null, null));
		logs.add(Log.newCompleteLog(1, null, false));
		logs.add(Log.newCompleteLog(1, null, true));
		
		logs.add(Log.newMatchLog(1, null, null, null));
		logs.add(Log.newCompleteLog(1, null, false));
		logs.add(Log.newCompleteLog(1, null, true));
		
		Assert.assertEquals(6, MatchingLogBatchWorker.getLogs(logs, 4, 10).size());
		Assert.assertEquals(3, logs.size());
	}
	
	@Test
	public void testBatchTooManyTrades() {
		ConcurrentLinkedDeque<Log> logs = new ConcurrentLinkedDeque<Log>();
		
		logs.add(Log.newMatchLog(1, null, null, null));
		logs.add(Log.newCompleteLog(1, null, false));
		logs.add(Log.newMatchLog(1, null, null, null));
		logs.add(Log.newCompleteLog(1, null, false));
		logs.add(Log.newMatchLog(1, null, null, null));
		logs.add(Log.newCompleteLog(1, null, false));
		logs.add(Log.newCompleteLog(1, null, true));
		
		Assert.assertEquals(6, MatchingLogBatchWorker.getLogs(logs, 3, 6).size());
		Assert.assertEquals(1, logs.size());
	}

}
