package conflux.dex.worker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import conflux.dex.model.OrderSide;

/**
 * Order depth aggregated by order price with specified precision.
 */
class DepthAggregate {
	
	private static final Comparator<BigDecimal> buyComparator = new PriceComparator(false);
	private static final Comparator<BigDecimal> sellComparator = new PriceComparator(true);
	
	private OrderSide side;
	private int priceScale;
	private int aggregatePriceScale;
	private NavigableMap<BigDecimal, DepthPriceLevel> levels;
	
	public DepthAggregate(OrderSide side, int priceScale, int aggregateStep) {
		if (aggregateStep < 0) {
			throw new IllegalArgumentException("aggregateStep should not be less than zero");
		}
		
		this.side = side;
		this.priceScale = priceScale;
		this.aggregatePriceScale = priceScale - aggregateStep;
		this.levels = new ConcurrentSkipListMap<BigDecimal, DepthPriceLevel>(side == OrderSide.Buy ? buyComparator : sellComparator);
	}
	
	private BigDecimal normalize(BigDecimal price) {
		RoundingMode mode = this.side == OrderSide.Buy ? RoundingMode.DOWN : RoundingMode.UP;
		return price.setScale(this.aggregatePriceScale, mode).setScale(this.priceScale);
	}
	
	public void add(BigDecimal price, BigDecimal amount) {
		price = this.normalize(price);
		
		DepthPriceLevel level = this.levels.putIfAbsent(price, new DepthPriceLevel(price, amount));
		if (level != null) {
			level.update(amount, 1);
		}
	}
	
	public void remove(BigDecimal price, BigDecimal amount, boolean orderRemoved) {
		price = this.normalize(price);
		
		DepthPriceLevel level = this.levels.get(price);
		level.update(amount.negate(), orderRemoved ? -1 : 0);
		
		if (level.getCount() == 0) {
			this.levels.remove(price);
		}
	}
	
	public List<DepthPriceLevel> getLevels(int depth) {
		List<DepthPriceLevel> levels = new ArrayList<DepthPriceLevel>(depth);
		
		for (BigDecimal price : this.levels.navigableKeySet()) {
			DepthPriceLevel level = this.levels.get(price);
			if (level != null) {
				levels.add(level.clone());
				
				if (levels.size() >= depth) {
					break;
				}
			}
		}
		
		return levels;
	}
	
	private static class PriceComparator implements Comparator<BigDecimal> {
		private boolean asc;
		
		public PriceComparator(boolean asc) {
			this.asc = asc;
		}

		@Override
		public int compare(BigDecimal o1, BigDecimal o2) {
			if (asc) {
				return o1.compareTo(o2);
			} else {
				return o2.compareTo(o1);
			}
		}
		
	}
}
