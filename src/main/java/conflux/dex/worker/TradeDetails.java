package conflux.dex.worker;

import conflux.dex.matching.Order;
import conflux.dex.model.Trade;

public class TradeDetails {
	private Trade trade;
	private Order takerOrder;
	private Order makerOrder;
	
	public TradeDetails(Trade trade, Order takerOrder, Order makerOrder) {
		this.trade = trade;
		this.takerOrder = takerOrder;
		this.makerOrder = makerOrder;
	}
	
	public Trade getTrade() {
		return trade;
	}
	
	public Order getTakerOrder() {
		return takerOrder;
	}
	
	public Order getMakerOrder() {
		return makerOrder;
	}
}
