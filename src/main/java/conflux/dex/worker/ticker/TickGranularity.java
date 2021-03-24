package conflux.dex.worker.ticker;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public interface TickGranularity {
	Instant truncate(Instant instant);
	
	int getValue();

	default Instant earlierOne(Instant instant){
		return diff(instant, -1);
	}
	default Instant laterOne(Instant instant) {
		return diff(instant, 1);
	}

	Instant diff(Instant instant, int diff);

	String getName();
	
	static TickGranularity ofMinutes(int minutes) {
		return new MinuteGranularity(minutes);
	}
}

class MinuteGranularity implements TickGranularity {
	private int minutes;
	
	public MinuteGranularity(int minutes) {
		if (minutes <= 0) {
			throw new IllegalArgumentException("minutes should be greater than 0");
		}
		
		this.minutes = minutes;
	}
	
	@Override
	public int getValue() {
		return this.minutes;
	}
	
	@Override
	public String getName() {
		return String.format("%dmin", this.minutes);
	}

	@Override
	public Instant truncate(Instant instant) {
		long epochSecs = instant.getEpochSecond();
		long diff = epochSecs % (60 * this.minutes);
		long ms = instant.toEpochMilli();
		long diffMs = ms % 1000;
		if (diff == 0 && diffMs == 0) {
			return instant;
		}
		return Instant.ofEpochSecond(epochSecs - diff);
	}

	@Override
	public Instant diff(Instant instant, int diff) {
		return instant.plus(diff, ChronoUnit.MINUTES);
	}

	@Override
	public Instant earlierOne(Instant instant) {
		return diff(instant, -minutes);
	}

	@Override
	public Instant laterOne(Instant instant) {
		return diff(instant, minutes);
	}
}