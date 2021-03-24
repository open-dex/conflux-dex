package conflux.dex.matching;

import java.math.BigDecimal;

import conflux.dex.common.Handler;

public interface LogHandler extends Handler<Log> {
	
	@Override
	default void handle(Log log) {
		switch (log.getType()) {
		case OrderMatched:
			this.onOrderMatched(log.getTakerOrder(), log.getMakerOrder(), log.getMatchAmount());
			break;
		case OrderPended:
			this.onOrderPended(log.getTakerOrder(), log.getMakerOrder());
			break;
		case TakerOrderOpened:
			this.onTakerOrderOpened(log.getTakerOrder());
			break;
		case MakerOrderCompleted:
			this.onMakerOrderCompleted(log.getMakerOrder());
			break;
		case TakerOrderCompleted:
			this.onTakerOrderCompleted(log.getTakerOrder());
			break;
		case MakerOrderCancelled:
			this.onMakerOrderCancelled(log.getMakerOrder(), false);
			break;
		case MakerOrderCancelledByAdmin:
			this.onMakerOrderCancelled(log.getMakerOrder(), true);
			break;
		case PendingOrderCancelled:
			this.onPendingOrderCancelled(log.getMakerOrder());
			break;
		case TakerOrderCancelled:
			this.onTakerOrderCancelled(log.getTakerOrder());
			break;
		case OrderPruned:
			this.onOrderPruned((PruneLog) log);
			break;
		default:
			throw new UnsupportedOperationException();
		}
	}
	
	void onOrderMatched(Order takerOrder, Order makerOrder, BigDecimal tradeAmount);
	void onOrderPended(Order takerOrder, Order makerOrder);
	void onTakerOrderOpened(Order order);
	void onMakerOrderCompleted(Order order);
	void onTakerOrderCompleted(Order order);
	void onMakerOrderCancelled(Order order, boolean byAdmin);
	void onPendingOrderCancelled(Order order);
	void onTakerOrderCancelled(Order order);
	
	default void onOrderPruned(PruneLog log) { }
	
}
