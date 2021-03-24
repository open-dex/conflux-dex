package conflux.dex.common.worker;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.codahale.metrics.Gauge;

import conflux.dex.common.Metrics;

public class WorkerError {
	
	private static final int MAX_RETRY = 5;
	
	private static ConcurrentMap<String, WorkerError> workerName2Errors = new ConcurrentHashMap<String, WorkerError>();
	
	static {
		Metrics.getOrAdd(new Gauge<SortedMap<String, WorkerError>>() {

			@Override
			public SortedMap<String, WorkerError> getValue() {
				return new TreeMap<String, WorkerError>(workerName2Errors);
			}
			
		}, WorkerError.class, "errors");
	}
	
	public String startTime;
	public long retry;
	public String data;
	public String message;
	public List<String> callstack;
	
	public WorkerError(String data) {
		this.startTime = Instant.now().toString();
		this.data = data;
	}
	
	static void clear(AsyncWorker<?> worker) {
		workerName2Errors.remove(worker.getName());
	}
	
	static void update(AsyncWorker<?> worker, String data, Exception e, long retry) {
		WorkerError error = workerName2Errors.computeIfAbsent(worker.getName(), name -> new WorkerError(data));
		error.retry = retry;
		
		String errMsg = e.getMessage();
		
		if (errMsg == null && error.message != null) {
			error.message = null;
			error.callstack = null;
		}
		
		if (errMsg != null && !errMsg.equals(error.message)) {
			error.message = errMsg;
			error.callstack = null;
		}
		
		if (error.callstack == null) {
			error.callstack = Arrays.asList(e.getStackTrace()).stream()
					.map(t -> t.toString())
					.collect(Collectors.toList());
		}
	}
	
	public static boolean hasUnrecoverableError() {
		return workerName2Errors.values().stream().anyMatch(e -> e.retry > MAX_RETRY);
	}
}
