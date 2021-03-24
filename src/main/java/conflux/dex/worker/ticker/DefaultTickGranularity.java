package conflux.dex.worker.ticker;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public enum DefaultTickGranularity implements TickGranularity {
	Minute(1, "1min"),
	Hour(60, "60min"),
	Day(60*24, "1day"),
	Week(60*24*7, "1week"),
	Month(60*24*30, "1month");
	
	public static ZoneId zoneId = ZoneId.systemDefault();
	
	private int value;
	private String name;
	
	private DefaultTickGranularity(int value, String name) {
		this.value = value;
		this.name = name;
	}
	
	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public Instant diff(Instant instant, int diff) {
		switch (this) {
			case Minute:
				return instant.plus(diff, ChronoUnit.MINUTES);
			case Hour:
				return instant.plus(diff, ChronoUnit.HOURS);
			case Day:
				return instant.plus(diff, ChronoUnit.DAYS);
			case Week:
				return instant.plus(diff*7, ChronoUnit.DAYS);
			case Month:
				return ZonedDateTime.ofInstant(instant, zoneId)
						.plusMonths(diff)
						.toInstant();
			default:
				throw new UnsupportedOperationException();
		}
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public Instant truncate(Instant instant) {
		switch (this) {
		case Minute:
			return instant.truncatedTo(ChronoUnit.MINUTES);
		case Hour:
			return instant.truncatedTo(ChronoUnit.HOURS);
		case Day:
			return ZonedDateTime.ofInstant(instant, zoneId)
					.truncatedTo(ChronoUnit.DAYS)
					.toInstant();
		case Week:
			return ZonedDateTime.ofInstant(instant, zoneId)
					.truncatedTo(ChronoUnit.DAYS)
					.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
					.toInstant();
		case Month:
			return ZonedDateTime.ofInstant(instant, zoneId)
					.truncatedTo(ChronoUnit.DAYS)
					.with(TemporalAdjusters.firstDayOfMonth())
					.toInstant();
		default:
			throw new UnsupportedOperationException();
		}
	}


	
	public static Instant localToday() {
		return Day.truncate(Instant.now());
	}
}
