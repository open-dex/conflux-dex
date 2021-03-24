package conflux.dex.model;

public enum FeeStrategy {
	/**
	 * Order matched for free.
	 */
	ForFree,
	/**
	 * Order matching fee to DEX.
	 */
	FeeToDex,
	/**
	 * Order matching fee to maker order.
	 */
	FeeToMaker,

}
