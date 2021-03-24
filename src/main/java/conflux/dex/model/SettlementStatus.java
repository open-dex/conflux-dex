package conflux.dex.model;

public enum SettlementStatus {
	/**
	 * Settled and updated in database.
	 */
	OffChainSettled,
	/**
	 * Transaction sent on blockchain.
	 */
	OnChainSettled,
	/**
	 * Transaction confirmed on blockchain.
	 */
	OnChainConfirmed,
	/**
	 * Transaction execution failed on blockchain.
	 */
	OnChainFailed,
	/**
	 * Transaction execution succeeded on blockchain, but failed to validate receipt (event logs).
	 */
	OnChainReceiptValidationFailed,
}
