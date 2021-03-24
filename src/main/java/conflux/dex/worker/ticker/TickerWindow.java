package conflux.dex.worker.ticker;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import conflux.dex.dao.TickDao;
import conflux.dex.model.Tick;
import conflux.dex.model.Trade;

public class TickerWindow {
	// Timeout to eliminate the out of date ticks from time window.
	private Duration timeout;
	
	// All ticks in the time window.
	private Deque<Tick> ticks = new LinkedList<Tick>();
	
	// Price queues to record the highest/lowest prices in the time window.
	// Once any tick eliminated from the time window, we must update the 
	// aggregated tick info for the new highest/lowest prices in queue.
	private PriceQueue highPrices = new PriceQueue(true);
	private PriceQueue lowPrices = new PriceQueue(false);
	
	// Use atomic object to support concurrency:
	// 1) update trade in sequence;
	// 2) read from external request;
	private AtomicReference<Tick> aggregate = new AtomicReference<Tick>();
	// Used to avoid updating time window every time when user request aggregated tick info.
	// Generally, if no trade for a short/long time that exceeds one minute, we must update
	// the time window before getting the aggregated tick info.
	private Instant lastTickTime;
	
	public TickerWindow() {
		this(Duration.ofDays(1));
	}
	
	public TickerWindow(Duration timeout) {
		this.timeout = timeout;
	}
	
	public TickerWindow(int productId, TickDao dao, int limit, TickGranularity granularity) {
		this.timeout = Duration.ofDays(1);
		
		Instant endTime = granularity.truncate(Instant.now());
		
		// in case of tick missed for the minute of oneDayBefore.
		Timestamp oneDayBefore = Timestamp.from(endTime.minus(this.timeout));
		List<Tick> ticks = dao.listTicks(productId, granularity.getValue(), oneDayBefore, 1);
		if (!ticks.isEmpty()) {
			this.move(ticks.get(0));
		}
		
		// initialize with ticks in last 24 hours
		ticks = dao.listTicks(productId, granularity.getValue(), oneDayBefore, Timestamp.from(endTime));
		for (Tick tick : ticks) {
			this.move(tick);
		}
	}
	
	public List<Tick> getTicks() {
		return new ArrayList<Tick>(this.ticks);
	}
	
	// Update new trade for the last tick.
	// For each product, it is updated in sequence without concurrency.
	public void update(Trade trade) {
		Tick agg = this.aggregate.get().clone();
		
		BigDecimal price = trade.getPrice();
		
		// update high prices if trade price is higher than the high price of last tick.
		if (this.highPrices.getLast().get().compareTo(price) < 0) {
			BigDecimal highest = this.highPrices.enqueue(price);
			agg.setHigh(highest);
		}
		
		// update low prices if trade price is lower than the low price of last tick.
		if (this.lowPrices.getLast().get().compareTo(price) > 0) {
			BigDecimal lowest = this.lowPrices.enqueue(price);
			agg.setLow(lowest);
		}
		
		agg.setClose(price);
		updateTickVolume(agg, trade.getAmount(), trade.getFunds(), 1);
		agg.setUpdateTime(trade.getCreateTime());
		
		this.aggregate.set(agg);
	}
	
	// Update new trades in batch for the last tick.
	// For each product, it is updated in sequence without concurrency.
	public void update(Tick tick) {
		Tick agg = this.aggregate.get().clone();
		
		// update high prices if trade price is higher than the high price of last tick.
		if (this.highPrices.getLast().get().compareTo(tick.getHigh()) < 0) {
			BigDecimal highest = this.highPrices.enqueue(tick.getHigh());
			agg.setHigh(highest);
		}
		
		// update low prices if trade price is lower than the low price of last tick.
		if (this.lowPrices.getLast().get().compareTo(tick.getLow()) > 0) {
			BigDecimal lowest = this.lowPrices.enqueue(tick.getLow());
			agg.setLow(lowest);
		}
		
		agg.setClose(tick.getClose());
		updateTickVolume(agg, tick.getBaseCurrencyVolume(), tick.getQuoteCurrencyVolume(), tick.getCount());
		agg.setUpdateTime(tick.getUpdateTime());
		
		this.aggregate.set(agg);
	}
	
	// Add new tick and update the time window.
	public synchronized void move(Tick ticker) {
		// first tick added
		if (this.aggregate.compareAndSet(null, ticker)) {
			this.ticks.addLast(ticker);
			this.highPrices.enqueue(ticker.getHigh());
			this.lowPrices.enqueue(ticker.getLow());
			this.lastTickTime = ticker.getCreateTime().toInstant();
			return;
		}
		
		Tick agg = this.aggregate.get().clone();
		
		// update the time window according to the time of new tick.
		this.removeTimeoutTicks(agg, ticker.getCreateTime());
		
		this.ticks.addLast(ticker);
		
		BigDecimal highest = this.highPrices.enqueue(ticker.getHigh());
		// in case of Close price of last removed tick > new highest price
		if (agg.getHigh().compareTo(highest) < 0) {
			agg.setHigh(highest);
		}
		
		BigDecimal lowest = this.lowPrices.enqueue(ticker.getLow());
		// in case of the Close price of last removed tick < new lowest price
		if (agg.getLow().compareTo(lowest) > 0) {
			agg.setLow(lowest);
		}
		
		agg.setClose(ticker.getClose());
		updateTickVolume(agg, ticker.getBaseCurrencyVolume(), ticker.getQuoteCurrencyVolume(), ticker.getCount());
		agg.setUpdateTime(ticker.getCreateTime());
		
		this.aggregate.set(agg);
		this.lastTickTime = ticker.getCreateTime().toInstant();
	}
	
	// remove the out of date ticks the specified endTime.
	private void removeTimeoutTicks(Tick agg, Timestamp endTime) {
		Timestamp startTime = Timestamp.from(endTime.toInstant().minus(this.timeout));
		if (agg.getCreateTime().before(startTime)) {
			agg.setCreateTime(startTime);
		}
		
		Tick lastRemovedTick = null;
		
		// remove ticks that out of date.
		while (!this.ticks.isEmpty() && this.ticks.getFirst().getCreateTime().before(startTime)) {
			lastRemovedTick = this.ticks.removeFirst();
			this.highPrices.dequeue(lastRemovedTick.getHigh());
			this.lowPrices.dequeue(lastRemovedTick.getLow());
			updateTickVolume(agg, lastRemovedTick.getBaseCurrencyVolume().negate(), lastRemovedTick.getQuoteCurrencyVolume().negate(), -lastRemovedTick.getCount());
		}
		
		if (this.ticks.isEmpty()) {
			// If any tick removed, set the Open/High/Low as the Close price of last removed tick.
			if (lastRemovedTick != null) {
				agg.setOpen(lastRemovedTick.getClose());
				agg.setHigh(lastRemovedTick.getClose());
				agg.setLow(lastRemovedTick.getClose());
			}
		} else if (this.ticks.getFirst().getCreateTime().after(startTime)) {
			// if removed, use the Close price of last removed one as the Open price for the new time window
			if (lastRemovedTick != null) {
				agg.setOpen(lastRemovedTick.getClose());
			}
			
			// the removed tick may be with the highest price
			BigDecimal highest = this.highPrices.getFirst().get();
			if (lastRemovedTick != null && lastRemovedTick.getClose().compareTo(highest) > 0) {
				agg.setHigh(lastRemovedTick.getClose());
			} else {
				agg.setHigh(highest);
			}
			
			// the removed tick may be with the lowest price
			BigDecimal lowest = this.lowPrices.getFirst().get();
			if (lastRemovedTick != null && lastRemovedTick.getClose().compareTo(lowest) < 0) {
				agg.setLow(lastRemovedTick.getClose());
			} else {
				agg.setLow(lowest);
			}
		} else {
			agg.setOpen(this.ticks.getFirst().getOpen());
			agg.setHigh(this.highPrices.getFirst().get());
			agg.setLow(this.lowPrices.getFirst().get());
		}
	}
	
	private static void updateTickVolume(Tick tick, BigDecimal baseVolDelta, BigDecimal quoteVolDelta, int countDelta) {
		tick.setBaseCurrencyVolume(tick.getBaseCurrencyVolume().add(baseVolDelta));
		tick.setQuoteCurrencyVolume(tick.getQuoteCurrencyVolume().add(quoteVolDelta));
		tick.setCount(tick.getCount() + countDelta);
	}
	
	public Tick getAggregate() {
		return this.getAggregate(Instant.now());
	}
	
	public synchronized Tick getAggregate(Instant endTime) {
		if (this.lastTickTime == null) {
			return this.aggregate.get();
		}
		
		endTime = DefaultTickGranularity.Minute.truncate(endTime);
		if (!this.lastTickTime.isBefore(endTime)) {
			return this.aggregate.get();
		}
		
		// no trade for a short/long time, need to move the time window forward.
		Tick agg = this.aggregate.get().clone();
		
		this.removeTimeoutTicks(agg, Timestamp.from(endTime));
		
		agg.setUpdateTime(Timestamp.from(endTime));
		
		this.aggregate.set(agg);
		this.lastTickTime = endTime;
		
		return agg;
	}
}

/*
 * PriceQueue is used to maintain a series of prices in a time window.
 * Take "max price first" as example:
 * 1) Enqueue
 * 		- Add item into queue if empty.
 * 		- If new price is higher than the first element, then clear the queue and add the new price item.
 * 		- Otherwise, append the new price item:
 * 			a) Remove lower prices from the last position.
 * 			b) If any element has same price, then increase the counter.
 * 			c) Otherwise, append the new price item.
 * 2) Dequeue
 * 		- Handle only when dequeued price is the same with the first element in queue.
 * 		- Decrease the counter of first element.
 * 		- Once counter decreased to 0, remove the first element.
 * 
 * When any tick out of date and eliminated from the time window, the corresponding queues of high/low prices
 * should also be dequeued with the price of removed tick. If the tick with highest price eliminated, time window
 * could update the highest value with remaining queued prices in time complexity O(1).
 */
class PriceQueue {
	
	private static class Item {
		public BigDecimal price;
		public int count;
		
		public Item(BigDecimal price) {
			this.price = price;
			this.count = 1;
		}
	}
	
	private boolean maxFirst;
	private Deque<Item> items = new LinkedList<Item>();
	
	public PriceQueue(boolean maxFirst) {
		this.maxFirst = maxFirst;
	}
	
	public Optional<BigDecimal> getFirst() {
		return this.items.isEmpty() ? Optional.empty() : Optional.of(this.items.getFirst().price);
	}
	
	public Optional<BigDecimal> getLast() {
		return this.items.isEmpty() ? Optional.empty() : Optional.of(this.items.getLast().price);
	}
	
	public BigDecimal enqueue(BigDecimal price) {
		if (this.items.isEmpty()) {
			this.items.addLast(new Item(price));
			return price;
		}
		
		int cmp = this.items.getFirst().price.compareTo(price);
		if ((this.maxFirst && cmp < 0) || (!this.maxFirst && cmp > 0)) {
			this.items.clear();
			this.items.addLast(new Item(price));
			return price;
		}
		
		while (!this.items.isEmpty()) {
			Item last = this.items.getLast();
			cmp = last.price.compareTo(price);
			
			if (cmp == 0) {
				last.count++;
				break;
			}
			
			if ((this.maxFirst && cmp > 0) || (!this.maxFirst && cmp < 0)) {
				this.items.add(new Item(price));
				break;
			}
			
			this.items.removeLast();
		}
		
		return this.items.getFirst().price;
	}
	
	public boolean dequeue(BigDecimal price) {
		if (this.items.isEmpty()) {
			return false;
		}
		
		Item first = this.items.getFirst();
		if (first.price.compareTo(price) != 0) {
			return false;
		}
		
		if (--first.count == 0) {
			this.items.removeFirst();
		}
		
		return true;
	}
	
}
