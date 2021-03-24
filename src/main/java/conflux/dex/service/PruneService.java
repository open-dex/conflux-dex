package conflux.dex.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.codahale.metrics.Histogram;
import com.google.common.collect.Lists;

import conflux.dex.common.Metrics;
import conflux.dex.config.PruneConfig;
import conflux.dex.dao.DexDao;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.User;

// Temporary solution to prune orders of market maker that never matched.
@Service
public class PruneService extends JdbcDaoSupport {
	
	private static final Logger logger = LoggerFactory.getLogger(PruneService.class);
	
	private static final String KEY_PRUNE_START = "prune.start";
	
	private static final Histogram latencyStat = Metrics.histogram(PruneService.class, "latency");
	private static final Histogram rowsStat = Metrics.histogram(PruneService.class, "rows");
	
	private DexDao dao;
	private Timestamp start = Timestamp.from(Instant.now());

	PruneConfig pruneConfig;
	@Value("${prune.retention.ms:60000}")
	private long pruneRetentionMillis = 60000; // 1 minute by default
	
	@Autowired
	public PruneService(DataSource ds, DexDao dao) {
		super.setDataSource(ds);
		
		this.dao = dao;
		
		Optional<String> lastStart = dao.getConfig(KEY_PRUNE_START);
		if (lastStart.isPresent()) {
			this.start = Timestamp.valueOf(lastStart.get());
		}
	}

	@Autowired
	public void setConfig(PruneConfig config) {
		this.pruneConfig = config;
		logger.info("set market maker address {}", config.getMarketmaker());
	}

	@Scheduled(initialDelay = 5000, fixedDelay = 5000)
	public void prune() {
		if (this.pruneConfig == null) {
			return;
		}
		List<String> marketmaker = this.pruneConfig.getMarketmaker();
		if (marketmaker == null) {
			return;
		}
		Timestamp to = new Timestamp(System.currentTimeMillis() - this.pruneRetentionMillis);
		if (this.start.after(to)) {
			return;
		}
		long start = System.currentTimeMillis();
		Long reduce = marketmaker.stream()
				.map(addr -> this.pruneMaker(addr, to))
				.reduce((a, b) -> a + b)
				.orElse(0L);

		long elapsed = System.currentTimeMillis() - start;
		rowsStat.update(reduce);
		latencyStat.update(elapsed);
		this.dao.setConfig(KEY_PRUNE_START, to.toString());
		this.start = to;
		if (elapsed >= 2000) {
			logger.warn("Deleting market maker's order costs too long time {} ms, records {}.", elapsed, reduce);
		}
	}
	public long pruneMaker(String makerAddr, Timestamp to) {
		logger.debug("prune maker {}", makerAddr);
		Optional<User> marketMaker = this.dao.getUserByName(makerAddr).get();
		if (!marketMaker.isPresent()) {
			return 0;
		}

		long marketMakerId = marketMaker.get().getId();


		String sql = "select id FROM t_order WHERE user_id = ? AND create_time BETWEEN ? AND ? AND status = ? AND filled_amount = 0";

		List<Long> ids = this.getJdbcTemplate().queryForList(sql, Long.class, marketMakerId, this.start, to, OrderStatus.Cancelled.name());
		long numRowsDeleted = ids.size();
		Lists.partition(ids, 500).forEach(partialIds->{
			List<Object[]> params = partialIds.stream().map(id -> new Object[]{id}).collect(Collectors.toList());
			this.getJdbcTemplate().batchUpdate("delete from t_order where id = ? ", params);
		});

		logger.debug("succeed to prune orders from market maker {}, from = {}, to = {}, deleted = {}",
				makerAddr, this.start, to, numRowsDeleted);
		return numRowsDeleted;
	}

}
