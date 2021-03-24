package conflux.dex.service.blockchain;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.influxdb.dto.Point.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.InfluxDBReportable;
import conflux.dex.common.Utils;
import conflux.dex.service.HealthService;
import conflux.dex.service.HealthService.PauseSource;
import conflux.web3j.Cfx;
import conflux.web3j.RpcException;

@Service
public class HeartBeatService implements InfluxDBReportable {
	private static final Logger logger = LoggerFactory.getLogger(HeartBeatService.class);
	
	private static final long MAX_FAILURES = 300;	// about 5 minutes
	private static final Timer perf = Metrics.timer(HeartBeatService.class, "perf");
	
	private Cfx cfx;
	private AtomicReference<BigInteger> currentEpoch;
	private AtomicBoolean unhealthy = new AtomicBoolean();
	private AtomicLong errorCounter = new AtomicLong();
	
	private HealthService healthService;
	
	@Autowired
	public HeartBeatService(Cfx cfx) {
		this.cfx = cfx;
		this.currentEpoch = new AtomicReference<BigInteger>(cfx.getEpochNumber().sendAndGet());
		
		Metrics.dumpReportable(this);
	}
	
	@Autowired
	public void setHealthService(HealthService healthService) {
		this.healthService = healthService;
	}
	
	public BigInteger getCurrentEpoch() {
		return this.currentEpoch.get();
	}
	
	public boolean isUnhealthy() {
		return this.unhealthy.get();
	}
	
	public long getErrorCount() {
		return this.errorCounter.get();
	}

	@Scheduled(initialDelay = 1000, fixedDelay = 1000)
	public void pollEpoch() {
		if (this.cfx == null) {
			return;
		}
		
		try (Context context = perf.time()) {
			BigInteger epochNum = this.cfx.getEpochNumber().sendAndGet(0, 0);
			this.onSucceeded(epochNum);
			logger.trace("current epoch number: {}", epochNum);
		} catch (RpcException e) {
			this.onFailed(e);
			if (Utils.isRpcError(e)) {
				logger.error("failed to get epoch number from blockchain: {}", e.getMessage());
			} else {
				logger.debug("failed to get epoch number from blockchain: {}", e.getMessage());
			}
		} catch (Exception e) {
			this.onFailed(e);
			logger.error("failed to get epoch number from blockchain for unexpected error", e);
		}
	}
	
	private void onSucceeded(BigInteger epochNum) {
		this.currentEpoch.set(epochNum);
		this.unhealthy.set(false);
		this.errorCounter.set(0);
	}
	
	private void onFailed(Exception e) {
		this.unhealthy.set(true);
		
		if (this.errorCounter.incrementAndGet() < MAX_FAILURES) {
			return;
		}
		
		if (this.healthService == null || this.healthService.getPauseSource().isPresent()) {
			return;
		}
		
		String error = String.format("hearbeat failed in the past 5 minutes: %s", e.getMessage());
		logger.error(error);
		this.healthService.pause(PauseSource.Blockchain, error);
	}

	@Override
	public Builder buildInfluxDBPoint(Builder builder) {
		return builder.addField("unhealthy", this.unhealthy.get())
				.addField("epoch", this.currentEpoch.get().longValue());
	}
}
