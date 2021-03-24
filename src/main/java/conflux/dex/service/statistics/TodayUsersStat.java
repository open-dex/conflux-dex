package conflux.dex.service.statistics;

import java.util.concurrent.atomic.AtomicLong;

import org.influxdb.dto.Point.Builder;
import org.springframework.stereotype.Component;

import conflux.dex.common.Metrics.ReportableGauge;
import conflux.dex.event.Events;
import conflux.dex.model.User;

@Component
public class TodayUsersStat extends TodayDataStat<User, Long> implements ReportableGauge<Long> {
	
	private AtomicLong counter = new AtomicLong();

	public TodayUsersStat() {
		super(Events.NEW_USER_ADDED);
	}

	@Override
	protected void update(User data) {
		this.counter.incrementAndGet();
	}

	@Override
	protected void reset() {
		this.counter.set(0);
	}

	@Override
	protected Long get() {
		return this.counter.get();
	}

	@Override
	public Builder buildInfluxDBPoint(Builder builder, Long value) {
		return builder.addField("value", value);
	}

}
