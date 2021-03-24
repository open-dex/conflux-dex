package conflux.dex.model;

public enum AccountStatus {
	Normal,
	/**
	 * Account is not allowed to place order.
	 */
	ForceWithdrawing,
}
