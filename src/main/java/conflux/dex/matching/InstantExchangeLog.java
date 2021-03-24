package conflux.dex.matching;

import java.util.List;

import conflux.dex.model.InstantExchangeProduct;

/**
 * Order matching log, including order matched, opened and cancelled.
 */
public class InstantExchangeLog extends Log {
	private InstantExchangeProduct product;
	private List<Log> quoteLogs;
	private List<Log> baseLogs;

	public static InstantExchangeLog newLog(InstantExchangeProduct product, Order takerOrder, List<Log> quoteLogs, List<Log> baseLogs, LogType type) {
		InstantExchangeLog log = new InstantExchangeLog();
		log.setProductId(product.getId());
		log.setTakerOrder(takerOrder);
		log.setQuoteLogs(quoteLogs);
		log.setBaseLogs(baseLogs);
		log.setProduct(product);
		log.setType(type);
		return log;
	}

	public static InstantExchangeLog newInstantExchangeLog(InstantExchangeProduct product, Order takerOrder, List<Log> quoteLogs, List<Log> baseLogs) {
		return newLog(product, takerOrder, quoteLogs, baseLogs, LogType.OrderMatched);
	}
	
	public static InstantExchangeLog newPendingCancelLog(InstantExchangeProduct product, Order order) {
		return newLog(product, order, null, null, LogType.PendingOrderCancelled);
	}
	
	public static InstantExchangeLog newPendingLog(InstantExchangeProduct product, Order order) {
		return newLog(product, order, null, null, LogType.OrderPended);
	}

	public InstantExchangeProduct getProduct() {
		return product;
	}

	public void setProduct(InstantExchangeProduct product) {
		this.product = product;
	}

	public List<Log> getQuoteLogs() {
		return quoteLogs;
	}

	public void setQuoteLogs(List<Log> quoteLogs) {
		this.quoteLogs = quoteLogs;
	}

	public List<Log> getBaseLogs() {
		return baseLogs;
	}

	public void setBaseLogs(List<Log> baseLogs) {
		this.baseLogs = baseLogs;
	}

}
