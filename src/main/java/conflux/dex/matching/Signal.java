package conflux.dex.matching;

public class Signal {
	private SignalType type;
	private int productId;
	
	private static Signal newSignal(SignalType type, int productId) {
		Signal signal = new Signal();
		signal.setType(type);
		signal.setProductId(productId);
		return signal;
	}
	
	public static Signal orderImportedSignal() {
		return newSignal(SignalType.OrderImported, 0);
	}
	
	public static Signal orderBookInitializedSignal(int productId) {
		return newSignal(SignalType.OrderBookInitialized, productId);
	}
	
	public static Signal cancelAllOrders() {
		return newSignal(SignalType.CancelAllOrders, 0);
	}
	
	public SignalType getType() {
		return type;
	}
	public void setType(SignalType type) {
		this.type = type;
	}
	public int getProductId() {
		return productId;
	}
	public void setProductId(int productId) {
		this.productId = productId;
	}
}
