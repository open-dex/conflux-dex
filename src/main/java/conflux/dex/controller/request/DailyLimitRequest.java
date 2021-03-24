package conflux.dex.controller.request;

import java.sql.Time;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

import conflux.dex.common.BusinessFault;

public class DailyLimitRequest extends AdminRequest {
	/**
	 * product name.
	 */
	public String product;
	/**
	 * start list. time format is "HH:MM:SS", e.g. "09:00:00"
	 */
	public List<String> startTimes;
	/**
	 * end list. time format is "HH:MM:SS", e.g. "09:00:00"
	 */
	public List<String> endTimes;

	public DailyLimitRequest() {
	}

	public DailyLimitRequest(String product, List<String> startTimes, List<String> endTimes) {
		this.product = product;
		this.startTimes = startTimes;
		this.endTimes = endTimes;
	}

	@Override
	protected void validate() {
		final String timeFmt = "([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]";
		// check length
		if (this.startTimes.size() != this.endTimes.size()) 
			throw BusinessFault.ProductInvalidDailyLimitParameter.rise();
		// check time format
		for (String startTime : this.startTimes)
			if (!Pattern.matches(timeFmt, startTime))
				throw BusinessFault.ProductInvalidDailyLimitParameter.rise();
		for (String endTime : this.endTimes)
			if (!Pattern.matches(timeFmt, endTime))
				throw BusinessFault.ProductInvalidDailyLimitParameter.rise();
		// check overlapping
		int n = this.startTimes.size();
		for (int i = 0; i < n; ++i) {
			Time iStartTime = Time.valueOf(this.startTimes.get(i));
			Time iEndTime = Time.valueOf(this.endTimes.get(i));
			for (int j = i + 1; j < n; ++j) {
				Time jStartTime = Time.valueOf(this.startTimes.get(j));
				Time jEndTime = Time.valueOf(this.endTimes.get(j));
				if (!(iStartTime.after(jEndTime) || iEndTime.before(jStartTime)))
					throw BusinessFault.ProductInvalidDailyLimitParameter.rise();
			}
		}
	}

	@Override
	protected List<RlpType> getEncodeValues() {
		return Arrays.asList(RlpString.create(this.product),
				new RlpList(this.startTimes.stream().map(x -> RlpString.create(x)).collect(Collectors.toList())),
				new RlpList(this.endTimes.stream().map(x -> RlpString.create(x)).collect(Collectors.toList())));
	}
}
