package conflux.dex.matching;

import java.util.List;

public class PruneLog extends Log {
	
	private PruneRequest request;
	private List<Order> orders;
	
	public static PruneLog create(int productId, PruneRequest request, List<Order> orders) {
		PruneLog log = new PruneLog();
		log.setProductId(productId);
		log.setType(LogType.OrderPruned);
		log.request = request;
		log.orders = orders;
		return log;
	}
	
	public PruneRequest getRequest() {
		return request;
	}
	
	public List<Order> getOrders() {
		return orders;
	}

}
