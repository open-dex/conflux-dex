package conflux.dex.matching;

public enum LogType {
	OrderMatched,
	OrderPended,
	TakerOrderOpened,
	MakerOrderCompleted,
	TakerOrderCompleted,
	MakerOrderCancelled,
	MakerOrderCancelledByAdmin,
	PendingOrderCancelled,
	TakerOrderCancelled,
	OrderBookInitialized,
	OrderBookStatusChanged,
	OrderPruned,
}
