package conflux.dex.service;

import java.util.Map;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Counting;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Sampling;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.InfluxDBReportable;
import conflux.dex.common.Metrics.QueueMetric;

@Service
public class MetricsReporter {
	
	private static Logger logger = LoggerFactory.getLogger(MetricsReporter.class);
	
	private static Timer perf = Metrics.timer(MetricsReporter.class, "perf");
	
	private InfluxDB influx;
	
	@Autowired
	public MetricsReporter(@Value("${monitoring.influx.url:}") String url,
			@Value("${monitoring.influx.user:}") String user,
			@Value("${monitoring.influx.password:}") String password,
			@Value("${monitoring.influx.database:}") String database) {
		if (!StringUtils.isEmpty(url) && !StringUtils.isEmpty(database)) {
			this.influx = StringUtils.isEmpty(user)
					? InfluxDBFactory.connect(url)
					: InfluxDBFactory.connect(url, user, password);
			this.influx.enableBatch();
			this.influx.setDatabase(database);
		}
	}
	
	@Scheduled(initialDelay = 10000, fixedDelay = 10000)
	public void report() {
		if (this.influx == null) {
			return;
		}
		
		try (Context context = perf.time()) {
			this.safeReport();
		} catch (InfluxDBException e) {
			logger.debug("failed to report metrics to InfluxDB", e);
	    } catch (Exception e) {
	    	logger.error("failed to report metrics to InfluxDB", e);
		}
	}
	
	private void safeReport() throws Exception {
		if (!this.influx.ping().isGood()) {
			return;
		}
		
		BatchPoints bp = this.snapshot();
		
		this.influx.write(bp);
	}
	
	private BatchPoints snapshot() {
		BatchPoints.Builder builder = BatchPoints.builder();
		
		for (Map.Entry<String, Metric> entry : Metrics.DefaultRegistry.getMetrics().entrySet()) {
			Metric metric = entry.getValue();
			Point.Builder pb = Point.measurement(entry.getKey());
			
			if (Counter.class.isInstance(metric)) {
				pb = this.buildCounting(pb, (Counter) metric);
			} else if (Gauge.class.isInstance(metric)) {
				if (InfluxDBReportable.class.isInstance(metric)) {
					pb = ((InfluxDBReportable) metric).buildInfluxDBPoint(pb);
				}
			} else if (Histogram.class.isInstance(metric)) {
				pb = this.buildCounting(pb, (Histogram) metric);
				pb = this.buildSampling(pb, (Histogram) metric);
			} else if (Meter.class.isInstance(metric)) {
				this.buildMetered(pb, (Meter) metric);
			} else if (Timer.class.isInstance(metric)) {
				pb = this.buildMetered(pb, (Timer) metric);
				pb = this.buildSampling(pb, (Timer) metric);
			} else if (QueueMetric.class.isInstance(metric)) {
				pb = this.buildQueueMetric(pb, (QueueMetric) metric);
			}
			
			if (pb.hasFields()) {
				builder.point(pb.build());
			}
		}
		
		return builder.build();
	}
	
	private Point.Builder buildCounting(Point.Builder builder, Counting counting) {
		return builder.addField("count", counting.getCount());
	}
	
	private Point.Builder buildSampling(Point.Builder builder, Sampling sampling) {
		Snapshot snapshot = sampling.getSnapshot();
		
		return builder.addField("min", snapshot.getMin())
				.addField("mean", snapshot.getMean())
				.addField("max", snapshot.getMax())
				.addField("stddev", snapshot.getStdDev())
				.addField("p50", snapshot.getMedian())
				.addField("p75", snapshot.get75thPercentile())
				.addField("p90", snapshot.getValue(0.9))
				.addField("p95", snapshot.get95thPercentile())
				.addField("p99", snapshot.get99thPercentile())
				.addField("p999", snapshot.get999thPercentile());
	}
	
	private Point.Builder buildMetered(Point.Builder builder, Metered metered) {
		return builder.addField("count", metered.getCount())
				.addField("m1", metered.getOneMinuteRate())
				.addField("m5", metered.getFiveMinuteRate())
				.addField("m15", metered.getFifteenMinuteRate())
				.addField("mean", metered.getMeanRate());
	}
	
	private Point.Builder buildQueueMetric(Point.Builder builder, QueueMetric queue) {
		return builder.addField("size", queue.getSize())
				.addField("enq.m1", queue.getEnqueue().getOneMinuteRate())
				.addField("enq.m5", queue.getEnqueue().getFiveMinuteRate())
				.addField("enq.m15", queue.getEnqueue().getFifteenMinuteRate())
				.addField("enq.mean", queue.getEnqueue().getMeanRate())
				.addField("deq.m1", queue.getDequeue().getOneMinuteRate())
				.addField("deq.m5", queue.getDequeue().getFiveMinuteRate())
				.addField("deq.m15", queue.getDequeue().getFifteenMinuteRate())
				.addField("deq.mean", queue.getDequeue().getMeanRate());
	}

}
