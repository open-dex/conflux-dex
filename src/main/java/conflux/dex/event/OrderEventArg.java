package conflux.dex.event;

import conflux.dex.model.Order;

public class OrderEventArg {
	
	public Order order;
	public String product;
	
	public OrderEventArg(Order order, String product) {
		this.order = order;
		this.product = product;
	}

}
