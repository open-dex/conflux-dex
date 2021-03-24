package conflux.dex.service.blockchain;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import conflux.dex.blockchain.CfxBuilder;
import conflux.dex.common.Metrics;
import conflux.dex.common.worker.SequentialWorker;
import conflux.dex.config.BlockchainConfig;
import conflux.web3j.Cfx;
import conflux.web3j.RpcException;

/**
 * TransactionRelayer sends transactions to multiple RPC servers, so that
 * temporary network issue will not lead to transaction not propagated timely.
 *
 * @see BlockchainSettlementService
 */
@Component
public class TransactionRelayer extends SequentialWorker<String> {
	
	private static Logger logger = LoggerFactory.getLogger(TransactionRelayer.class);
	
	private String config;
	private Map<String, Server> servers = new ConcurrentHashMap<String, Server>();
	
	@Autowired
	public TransactionRelayer(ExecutorService executor) {
		super(executor, TransactionRelayer.class.getSimpleName());
		
		this.setQueueMetric(Metrics.queue(TransactionRelayer.class));
		this.setHandleDataPerf(Metrics.timer(TransactionRelayer.class, "perf"));
		
		Metrics.dump(this);
	}
	
	private void updateServers() {
		if (StringUtils.isEmpty(BlockchainConfig.instance.txRelayerUrls)) {
			return;
		}
		
		if (BlockchainConfig.instance.txRelayerUrls.equalsIgnoreCase(this.config)) {
			return;
		}
		
		// update tx relayers only when config changed.
		this.config = BlockchainConfig.instance.txRelayerUrls;
		
		Set<String> urls = Arrays.asList(this.config.split(",")).stream()
				.map(url -> url.trim().toLowerCase())
				.filter(url -> !StringUtils.isEmpty(url))
				.collect(Collectors.toSet());
		
		// remove servers
		Iterator<String> it = this.servers.keySet().iterator();
		while (it.hasNext()) {
			if (!urls.contains(it.next())) {
				it.remove();
			}
		}
		
		// add new servers
		for (String url : urls) {
			this.servers.computeIfAbsent(url, k -> new Server(k));
		}
	}
	
	public Map<String, Server> getServers() {
		return servers;
	}

	@Override
	protected void doWork(String data) throws Exception {
		this.updateServers();
		
		for (Server server : this.servers.values()) {
			server.send(data);
		}
	}
	
	public static class Server {
		
		private static final int NUM_ERRORS_TO_SKIP = 10;
		private static final int SKIP_TIMEOUT_MILLIS = 300_000;	// 5 minutes
		
		public String url;
		@JsonIgnore
		public Cfx cfx;
		
		public long numTotal;
		public long numRpcErrors;
		public long numIoErrors;
		public long numUnknownErrors;
		
		public long skipCounter;
		public long skipTime;
		
		public Server(String url) {
			this.url = url;
			// need not to retry on IO error
			this.cfx = new CfxBuilder(url).build();
		}
		
		public void send(String signedTx) {
			// Current server may be skipped due to continuous non-PRC errors.
			if (this.skipTime > 0) {
				if (System.currentTimeMillis() - this.skipTime <= SKIP_TIMEOUT_MILLIS) {
					return;
				}
				
				// recovered
				this.skipTime = 0;
			}
			
			try {
				this.cfx.sendRawTransaction(signedTx).send();
				this.skipCounter = 0;
			} catch (RpcException e) {
				logger.trace("failed to send transaction due to RPC error", e);
				this.numRpcErrors++;
				this.skipCounter = 0;
			} catch (IOException e) {
				logger.debug("failed to send transaction due to IO error", e);
				this.numIoErrors++;
				this.skipCounter++;
			} catch (Exception e) {
				logger.warn("failed to send transaction due to unexpected error", e);
				this.numUnknownErrors++;
				this.skipCounter++;
			}
			
			this.numTotal++;
			
			// Skip current server due to continuous non-PRC errors.
			if (this.skipCounter >= NUM_ERRORS_TO_SKIP) {
				this.skipCounter = 0;
				this.skipTime = System.currentTimeMillis();
			}
		}
	}
	
}
