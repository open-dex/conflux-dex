package conflux.dex.model;

import java.util.Collections;
import java.util.List;

public class PagingResult<T> {
	/**
	 * Requested offset to fetch paging data.
	 */
	private int offset;
	/**
	 * Requested limit to fetch paging data.
	 */
	private int limit;
	/**
	 * Fetched paging data.
	 */
	private List<T> items;
	/**
	 * Total number of data.
	 */
	private int total;
	
	public PagingResult(int offset, int limit, List<T> items, int total) {
		this.offset = offset;
		this.limit = limit;
		this.items = items == null ? Collections.emptyList() : items;
		this.total = total;
	}
	
	public static <T> PagingResult<T> empty(int offset, int limit) {
		return new PagingResult<T>(offset, limit, null, 0);
	}
	
	public static <T> PagingResult<T> fromList(int offset, int limit, List<T> all) {
		List<T> paged = null;
		if (all != null && offset >= 0 && offset < all.size() && limit > 0) {
			int toIndex = Math.min(all.size(), offset + limit);
			paged = all.subList(offset, toIndex);
		}
		
		return new PagingResult<T>(offset, limit, paged, all == null ? 0 : all.size());
	}

	public int getOffset() {
		return offset;
	}

	public int getLimit() {
		return limit;
	}

	public List<T> getItems() {
		return items;
	}

	public int getTotal() {
		return total;
	}
	
	@Override
	public String toString() {
		return String.format("PagingResult{offset=%d, limit=%d, items=%d, total=%d}", 
				this.offset, this.limit, this.items.size(), this.total);
	}
}
