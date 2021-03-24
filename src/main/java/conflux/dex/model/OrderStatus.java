package conflux.dex.model;

public enum OrderStatus {
	New,
	Open,
	Cancelling,
	Cancelled,
	Filled,
	/**
	 * Pending orders are the orders placed when the trade is closing, or whose price is out of the daily limit range.
	 */
	Pending;
	
	public boolean isCompleted() {
		return this.equals(Cancelled) || this.equals(Filled);
	}
}
