package conflux.dex.matching;

import conflux.dex.common.Utils;

public class PruneRequest {
	
	private long startTimeInclusive;
	private long endTimeExclusive;
	private int totalProducts;
	private int productIndex;
	
	public static PruneRequest create(long startTimeInclusive, long endTimeExclusive) {
		PruneRequest request = new PruneRequest();
		request.startTimeInclusive = startTimeInclusive;
		request.endTimeExclusive = endTimeExclusive;
		return request;
	}
	
	public static PruneRequest create(PruneRequest request, int total, int index) {
		PruneRequest cloned = new PruneRequest();
		cloned.startTimeInclusive = request.startTimeInclusive;
		cloned.endTimeExclusive = request.endTimeExclusive;
		cloned.totalProducts = total;
		cloned.productIndex = index;
		return cloned;
	}
	
	public long getStartTimeInclusive() {
		return startTimeInclusive;
	}
	
	public void setStartTimeInclusive(long startTimeInclusive) {
		this.startTimeInclusive = startTimeInclusive;
	}
	
	public long getEndTimeExclusive() {
		return endTimeExclusive;
	}
	
	public void setEndTimeExclusive(long endTimeExclusive) {
		this.endTimeExclusive = endTimeExclusive;
	}
	
	public int getTotalProducts() {
		return totalProducts;
	}
	
	public void setTotalProducts(int totalProducts) {
		this.totalProducts = totalProducts;
	}
	
	public int getProductIndex() {
		return productIndex;
	}
	
	public void setProductIndex(int productIndex) {
		this.productIndex = productIndex;
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}

}
