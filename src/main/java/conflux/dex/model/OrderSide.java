package conflux.dex.model;

public enum OrderSide {
	Buy,
	Sell;
	
	public OrderSide opposite() {
		return this == Buy ? Sell : Buy;
	}
}
