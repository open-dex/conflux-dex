package conflux.dex.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.influxdb.dto.Point.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.InfluxDBReportable;
import conflux.dex.common.SignatureValidator;
import conflux.dex.common.worker.WorkerError;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;

@Service
public class HealthService implements InfluxDBReportable {
	
	public static enum PauseSource {
		Manual,
		Workers,
		Blockchain,
	}
	
	private static final Logger logger = LoggerFactory.getLogger(HealthService.class);
	private static final String CONFIG_KEY_PAUSED_SOURCE = "health.paused.source";
	private static final int MAX_ERRORS = 30;
	
	private DexDao dao;
	private Optional<PauseSource> pauseSource = Optional.empty();
	private LinkedList<String> recentErrors = new LinkedList<String>();
	
	@Autowired
	public HealthService(DexDao dao) {
		this.dao = dao;
		
		Metrics.dumpReportable(this);
		
		Events.WORKER_ERROR.addHandler(error -> pause(PauseSource.Workers, error));
		Events.BLOCKCHAIN_ERROR.addHandler(error -> pause(PauseSource.Blockchain, error));
	}
	
	@PostConstruct
	public void init() {
		Optional<String> source = this.dao.getConfig(CONFIG_KEY_PAUSED_SOURCE);
		if (!source.isPresent() || StringUtils.isEmpty(source.get())) {
			return;
		}
		
		SignatureValidator.DEFAULT.setDisabled(true);
		this.pauseSource = Optional.of(Enum.valueOf(PauseSource.class, source.get()));
		this.recentErrors.addFirst(this.pauseSource.get().toString());
	}
	
	public synchronized void pause(PauseSource source, String error) {
		this.dao.setConfig(CONFIG_KEY_PAUSED_SOURCE, source.toString());
		SignatureValidator.DEFAULT.setDisabled(true);
		this.pauseSource = Optional.of(source);
		this.recentErrors.addFirst(error);
		
		while (this.recentErrors.size() > MAX_ERRORS) {
			this.recentErrors.removeLast();
		}
		
		logger.info("system paused: source = {}, reason = {}", source, error);
	}
	
	public synchronized Optional<PauseSource> getPauseSource() {
		return this.pauseSource;
	}
	
	public synchronized String getPauseSourceString() {
		return this.pauseSource.isPresent() ? this.pauseSource.get().toString() : "";
	}
	
	public synchronized List<String> getRecentErrors() {
		return this.recentErrors;
	}
	
	public boolean isPausedBy(PauseSource source) {
		return this.pauseSource.isPresent() && this.pauseSource.get() == source;
	}
	
	public synchronized void resume() {
		this.dao.setConfig(CONFIG_KEY_PAUSED_SOURCE, "");
		SignatureValidator.DEFAULT.setDisabled(false);
		this.pauseSource = Optional.empty();
		this.recentErrors.clear();
	}
	
	@Scheduled(initialDelay = 5000, fixedDelay = 5000)
	public void checkWorkers() {
		if (!this.pauseSource.isPresent() && WorkerError.hasUnrecoverableError()) {
			this.pause(PauseSource.Workers, "Any worker has unrecoverable error");
		}
	}

	@Override
	public Builder buildInfluxDBPoint(Builder builder) {
		boolean paused = this.pauseSource.isPresent();
		String reason = this.pauseSource.isPresent() ? this.pauseSource.get().toString() : "";
		String details = paused && !this.recentErrors.isEmpty() ? this.recentErrors.get(0) : "";
		
		return builder.addField("paused", paused)
				.addField("reason", reason)
				.addField("details", details);
	}

}
