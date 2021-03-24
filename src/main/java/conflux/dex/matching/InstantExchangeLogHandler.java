package conflux.dex.matching;

import java.util.List;

import conflux.dex.model.InstantExchangeProduct;

public interface InstantExchangeLogHandler {
	
	default void handle(InstantExchangeLog log) {
		switch (log.getType()) {
		case OrderMatched:
			this.onInstantExchangeOrderMatched(log.getProduct(), log.getTakerOrder(), log.getQuoteLogs(), log.getBaseLogs());
			break;
		case OrderPended:
			this.onInstantExchangeOrderPended(log.getTakerOrder());
			break;
		case PendingOrderCancelled:
			this.onInstantExchangePendingOrderCancelled(log.getMakerOrder());
			break;
		default:
			throw new UnsupportedOperationException();
		}
	}
	
	void onInstantExchangePendingOrderCancelled(Order order);
	void onInstantExchangeOrderPended(Order order);
	void onInstantExchangeOrderMatched(InstantExchangeProduct product, Order takerOrder, List<Log> quoteLogs, List<Log> baseLogs);
}