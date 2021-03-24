package conflux.dex.model;

import java.sql.Time;

import conflux.dex.common.Utils;

public class DailyLimit {
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

	/**
	 * Daily limit id. (auto-generated)
	 */
	private int id;
	/**
	 * product id.
	 */
	private int productId;
	/**
	 * start time (open trading).
	 */
	private Time startTime;
	/**
	 * end time (close trading).
	 */
	private Time endTime;
	
	public static DailyLimit newDailyLimit(int productId, String startTime, String endTime) {
		DailyLimit dailyLimit = new DailyLimit();
		dailyLimit.productId = productId;
		dailyLimit.startTime = Time.valueOf(startTime);
		dailyLimit.endTime = Time.valueOf(endTime);
		return dailyLimit;
	}

	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public int getProductId() {
		return productId;
	}
	
	public void setProductId(int productId) {
		this.productId = productId;
	}
	
	public Time getStartTime() {
		return startTime;
	}
	
	public void setStartTime(Time startTime) {
		this.startTime = startTime;
	}
	
	public Time getEndTime() {
		return endTime;
	}
	
	public void setEndTime(Time endTime) {
		this.endTime = endTime;
	}
}
