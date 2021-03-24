package conflux.dex.worker.ticker;

import static org.junit.Assert.assertEquals;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;

import org.junit.Test;

public class TickGranularityTest {
	
	private void testUTC(TickGranularity granularity, String tradeInstant, String tickInstant) {
		assertEquals(Instant.parse(tickInstant), granularity.truncate(Instant.parse(tradeInstant)));
	}
	
	private void testLocal(TickGranularity granularity, String tradeDateTime, String tickDateTime) throws Exception {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		assertEquals(df.parse(tickDateTime).toInstant(), granularity.truncate(df.parse(tradeDateTime).toInstant()));
	}
	
	@Test
	public void testDefaultTickGranularity() throws Exception {
		this.testUTC(DefaultTickGranularity.Minute, "2019-12-09T09:30:00.123Z", "2019-12-09T09:30:00Z");
		this.testUTC(DefaultTickGranularity.Minute, "2019-12-09T09:30:01.123Z", "2019-12-09T09:30:00Z");
		this.testUTC(DefaultTickGranularity.Minute, "2019-12-09T09:30:59.123Z", "2019-12-09T09:30:00Z");
		
		this.testUTC(DefaultTickGranularity.Hour, "2019-12-09T09:00:00.123Z", "2019-12-09T09:00:00Z");
		this.testUTC(DefaultTickGranularity.Hour, "2019-12-09T09:59:59.123Z", "2019-12-09T09:00:00Z");
		
		this.testLocal(DefaultTickGranularity.Day, "2020-03-18 23:59:59", "2020-03-18 00:00:00");
		this.testLocal(DefaultTickGranularity.Day, "2020-03-19 00:00:00", "2020-03-19 00:00:00");
		this.testLocal(DefaultTickGranularity.Day, "2020-03-19 00:00:01", "2020-03-19 00:00:00");
		
		// 202019-03-16 is Monday
		this.testLocal(DefaultTickGranularity.Week, "2020-03-16 09:30:00", "2020-03-16 00:00:00");
		this.testLocal(DefaultTickGranularity.Week, "2020-03-17 09:30:00", "2020-03-16 00:00:00");
		this.testLocal(DefaultTickGranularity.Week, "2020-03-18 09:30:00", "2020-03-16 00:00:00");
		this.testLocal(DefaultTickGranularity.Week, "2020-03-23 09:30:00", "2020-03-23 00:00:00");
		
		this.testLocal(DefaultTickGranularity.Month, "2019-12-01 00:00:00", "2019-12-01 00:00:00");
		this.testLocal(DefaultTickGranularity.Month, "2019-12-01 09:30:00", "2019-12-01 00:00:00");
		this.testLocal(DefaultTickGranularity.Month, "2019-12-09 09:30:00", "2019-12-01 00:00:00");
		this.testLocal(DefaultTickGranularity.Month, "2019-12-31 23:59:59", "2019-12-01 00:00:00");
		this.testLocal(DefaultTickGranularity.Month, "2020-01-01 00:00:00", "2020-01-01 00:00:00");
	}
	
	@Test
	public void testMinutesGranularity() {
		// m5
		TickGranularity m5 = TickGranularity.ofMinutes(5);
		this.testUTC(m5, "2019-12-09T09:30:00.123Z", "2019-12-09T09:30:00Z");
		this.testUTC(m5, "2019-12-09T09:34:59.123Z", "2019-12-09T09:30:00Z");
		this.testUTC(m5, "2019-12-09T09:35:00.123Z", "2019-12-09T09:35:00Z");
		
		// m15
		TickGranularity m15 = TickGranularity.ofMinutes(15);
		this.testUTC(m15, "2019-12-09T09:30:00.123Z", "2019-12-09T09:30:00Z");
		this.testUTC(m15, "2019-12-09T09:44:59.123Z", "2019-12-09T09:30:00Z");
		this.testUTC(m15, "2019-12-09T09:45:00.123Z", "2019-12-09T09:45:00Z");
		
		// m30
		TickGranularity m30 = TickGranularity.ofMinutes(30);
		this.testUTC(m30, "2019-12-09T09:30:00.123Z", "2019-12-09T09:30:00Z");
		this.testUTC(m30, "2019-12-09T09:59:59.123Z", "2019-12-09T09:30:00Z");
		this.testUTC(m30, "2019-12-09T10:00:00.123Z", "2019-12-09T10:00:00Z");
	}

}
