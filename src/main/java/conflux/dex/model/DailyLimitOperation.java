package conflux.dex.model;

import conflux.dex.common.Utils;

public class DailyLimitOperation {
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

	/**
	 * product id.
	 */
	private int productId;
	/**
	 * Order type: "Open", "Close".
	 */
	private DailyLimitOperationType type;
	
	private DailyLimitOperation(int productId, DailyLimitOperationType type) {
		this.productId = productId;
		this.type = type;
	}
	
	public static DailyLimitOperation closeTrade(int productId) {
		return new DailyLimitOperation(productId, DailyLimitOperationType.Close);
	}
	
	public static DailyLimitOperation openTrade(int productId) {
		return new DailyLimitOperation(productId, DailyLimitOperationType.Open);
	}
	
	public int getProductId() {
		return productId;
	}

	public void setProductId(int productId) {
		this.productId = productId;
	}

	public DailyLimitOperationType getType() {
		return type;
	}

	public void setType(DailyLimitOperationType type) {
		this.type = type;
	}
}
