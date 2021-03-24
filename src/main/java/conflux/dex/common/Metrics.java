package conflux.dex.common;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;

public class Metrics {
	
	public static final MetricRegistry DefaultRegistry = SharedMetricRegistries.setDefault("default");
	
	@SuppressWarnings("unchecked")
	public static <T extends Metric> T getOrAdd(T metric, Class<?> type, String... names) {
		String name = MetricRegistry.name(type, names);
		
		Metric existing = DefaultRegistry.getMetrics().get(name);
		if (existing != null) {
			return (T) existing;
		}
		
		return DefaultRegistry.register(name, metric);
	}
	
	public static Counter counter(Class<?> type, String... names) {
		return DefaultRegistry.counter(MetricRegistry.name(type, names));
	}
	
	public static LongGauge longGauge(Class<?> type, String... names) {
		return getOrAdd(new LongGauge(), type, names);
	}
	
	public static <T> Gauge<T> dump(T obj, String... names) {
		return getOrAdd(new Gauge<T>() {

			@Override
			public T getValue() {
				return obj;
			}
			
		}, obj.getClass(), MetricRegistry.name("dump", names));
	}
	
	public static <T extends InfluxDBReportable> ReportableGauge<T> dumpReportable(T obj, String... names) {
		return getOrAdd(new ReportableGauge<T>() {

			@Override
			public T getValue() {
				return obj;
			}

			@Override
			public Builder buildInfluxDBPoint(Builder builder, T value) {
				return value.buildInfluxDBPoint(builder);
			}
			
		}, obj.getClass(), MetricRegistry.name("dump", names));
	}
	
	public static Histogram histogram(Class<?> type, String... names) {
		return DefaultRegistry.histogram(MetricRegistry.name(type, names));
	}
	
	public static Meter meter(Class<?> type, String... names) {
		return DefaultRegistry.meter(MetricRegistry.name(type, names));
	}
	
	public static Timer timer(Class<?> type, String... names) {
		return DefaultRegistry.timer(MetricRegistry.name(type, names));
	}
	
	public static void update(Timer timer, long startTimeMillis) {
		timer.update(System.currentTimeMillis() - startTimeMillis, TimeUnit.MILLISECONDS);
	}
	
	public static QueueMetric queue(Class<?> type) {
		return getOrAdd(new QueueMetric(), type, "queue");
	}
	
	public static QueueMetric queue(Class<?> type, String name) {
		return getOrAdd(new QueueMetric(), type, "queue", name);
	}
	
	public static class LongGauge implements Gauge<Long>, InfluxDBReportable {
		
		private AtomicLong value = new AtomicLong();

		@Override
		public Long getValue() {
			return this.value.get();
		}
		
		public void setValue(long value) {
			this.value.set(value);
		}

		@Override
		public Builder buildInfluxDBPoint(Builder builder) {
			return builder.addField("value", this.value.get());
		}
		
	}
	
	public static class QueueMetric implements Metric {
		private Meter enqueue = new Meter();
		private Meter dequeue = new Meter();
		
		public Meter getEnqueue() {
			return enqueue;
		}
		
		public Meter getDequeue() {
			return dequeue;
		}
		
		public long getSize() {
			return this.enqueue.getCount() - this.dequeue.getCount();
		}
		
		public void enqueue() {
			this.enqueue.mark();
		}
		
		public void enqueue(long n) {
			this.enqueue.mark(n);
		}
		
		public void dequeue() {
			this.dequeue.mark();
		}
		
		public void dequeue(long n) {
			this.dequeue.mark(n);
		}
	}
	
	public static interface InfluxDBReportable {
		
		Point.Builder buildInfluxDBPoint(Point.Builder builder);
		
	}
	
	public static interface ReportableGauge<T> extends Gauge<T>, InfluxDBReportable {
		
		Point.Builder buildInfluxDBPoint(Point.Builder builder, T value);
		
		@Override
		default Point.Builder buildInfluxDBPoint(Point.Builder builder) {
			return this.buildInfluxDBPoint(builder, this.getValue());
		}
		
	}

}
