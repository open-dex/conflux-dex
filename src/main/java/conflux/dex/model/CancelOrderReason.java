package conflux.dex.model;

public enum CancelOrderReason {
	CustomerRequested,
	MarketOrderPartialFilled,
	OnChainForceWithdrawRequested,
	AdminRequested,
}
