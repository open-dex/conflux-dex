package conflux.dex.model;

public enum WithdrawType {
	/**
	 * Submit withdraw request off chain.
	 */
	OffChain,
	/**
	 * Submit withdraw request on chain.
	 */
	OnChainRequest,
	/**
	 * Force withdraw.
	 */
	OnChainForce,
}
