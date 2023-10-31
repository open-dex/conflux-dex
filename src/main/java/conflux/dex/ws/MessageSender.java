package conflux.dex.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.QueueMetric;
import conflux.dex.common.worker.SequentialWorker;

class MessageSender extends SequentialWorker<TopicResponse> implements Subscriber {
	
	private static final Logger logger = LoggerFactory.getLogger(MessageSender.class);
	private static final TopicResponse PING = new TopicResponse();
	
	private Set<String> subscribedTopics = Collections.synchronizedSet(new HashSet<String>());
	private Random rand = new Random();
	private ByteBuffer pingBuf = ByteBuffer.allocate(Long.BYTES);
	private long connectedTime = System.currentTimeMillis();
	private Session session;
	private ObjectMapper mapper;
	
	// total statistics for all subscribers
	private static final QueueMetric sendQueue = Metrics.queue(MessageSender.class);
	private static final Timer sendPerf = Metrics.timer(MessageSender.class, "perf");
	private static final Meter tpsIoError = Metrics.meter(MessageSender.class, "error.io");
	private static final Meter tpsError = Metrics.meter(MessageSender.class, "error.others");

	public MessageSender(ExecutorService executor, Session session, ObjectMapper mapper) {
		super(executor, "MessageSender-" + session.getId());
		
		this.setQueueMetric(sendQueue);
		this.setHandleDataPerf(sendPerf);
		
		this.session = session;
		this.mapper = mapper;
	}

	@Override
	protected void doWork(TopicResponse data) throws Exception {
		try {
			if (data == PING) {
				this.pingBuf.putLong(0, this.rand.nextLong());
				this.session.getBasicRemote().sendPing(this.pingBuf);
			} else {
				String json = this.mapper.writeValueAsString(data);
				this.session.getBasicRemote().sendText(json);
			}
		} catch (JsonProcessingException e) {
			logger.error("failed to send message (JSON)", e);
			this.close();
		} catch (IOException e) {
			logger.debug("failed to send message (IO)", e);
			tpsIoError.mark();
			this.close();
		} catch (Exception e) {
			logger.error("failed to send message (Other)", e);
			tpsError.mark();
			this.close();
		}
	}

	@Override
	public String getId() {
		return this.session.getId();
	}
	
	@Override
	public long getConnectedTime() {
		return this.connectedTime;
	}

	@Override
	public Set<String> getSubscribedTopics() {
		return this.subscribedTopics;
	}

	@Override
	public void consume(TopicResponse data) {
		this.submit(data);
	}
	
	@Override
	public void ping() {
		this.submit(PING);
	}

	@Override
	public void close() {
		try {
			this.session.close();
		} catch (IOException e) {
			logger.debug("session has already been closed", e);
		}
	}

}
