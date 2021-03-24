package conflux.dex.worker.ticker;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import conflux.dex.dao.TickDao;
import conflux.dex.event.Events;
import conflux.dex.model.Tick;
import conflux.dex.model.Trade;

public class Ticker {
	public static final TickGranularity DEFAULT_GRANULARITIES[] = new TickGranularity[] {
		DefaultTickGranularity.Minute,
		TickGranularity.ofMinutes(5),
		TickGranularity.ofMinutes(15),
		TickGranularity.ofMinutes(30),
		DefaultTickGranularity.Hour,
		DefaultTickGranularity.Day,
		DefaultTickGranularity.Week,
		DefaultTickGranularity.Month,
	};

	public static final Map<Integer, TickGranularity> value2granularityMap
			= Arrays.stream(DEFAULT_GRANULARITIES)
			.collect(ImmutableMap.toImmutableMap(TickGranularity::getValue, Functions.identity()));

	private TickDao dao;
	private Map<TickGranularity, Tick> lastTicks = new HashMap<TickGranularity, Tick>();
	private TickerWindow last24HoursTickerWindow;
	
	public Ticker(int productId, TickDao dao) {
		this(productId, dao, DEFAULT_GRANULARITIES);
	}
	
	public Ticker(int productId, TickDao dao, TickGranularity granularities[]) {
		if (granularities == null || granularities.length == 0) {
			throw new IllegalArgumentException("granularities is null or empty");
		}
		
		this.dao = dao;
		
		for (TickGranularity granularity : granularities) {
			Optional<Tick> tick = dao.getLastTick(productId, granularity.getValue());
			if (tick.isPresent()) {
				this.lastTicks.put(granularity, tick.get());
			} else {
				this.lastTicks.put(granularity, Tick.empty());
			}
		}
		
		this.last24HoursTickerWindow = new TickerWindow(productId, dao, 60*24, DefaultTickGranularity.Minute);
	}

	public void update(Trade trade) {
		this.update(trade, this.dao);
	}
	
	public void update(Trade trade, TickDao tx) {
		for (Map.Entry<TickGranularity, Tick> entry : this.lastTicks.entrySet()) {
			this.handle(tx, trade, entry.getKey(), entry.getValue());
		}
	}
	
	public void update(Tick tick, TickDao tx) {
		for (Map.Entry<TickGranularity, Tick> entry : this.lastTicks.entrySet()) {
			this.handle(dao, tick, entry.getKey(), entry.getValue());
		}
	}

	private void handle(TickDao dao, Trade trade, TickGranularity granularity, Tick lastTick) {
		Instant lastTickInstant = lastTick.getCreateTime().toInstant();
		Instant tickInstant = granularity.truncate(trade.getCreateTime().toInstant());
		
		if (lastTickInstant.equals(tickInstant)) {
			lastTick.update(trade);
			dao.updateTick(lastTick);
			Events.TICK_CHANGED.fire(lastTick.clone());
			if (granularity == DefaultTickGranularity.Minute) {
				this.last24HoursTickerWindow.update(trade);
			}
		} else {
			Tick newTick = Tick.open(trade.getProductId(), granularity.getValue(), trade.getPrice(), trade.getAmount(), tickInstant);
			setOpenPrice(lastTick, newTick, trade.getPrice());
			dao.addTick(newTick);
			this.lastTicks.replace(granularity, lastTick, newTick);
			Events.TICK_CHANGED.fire(newTick.clone());
			if (granularity == DefaultTickGranularity.Minute) {
				this.last24HoursTickerWindow.move(newTick);
			}
		}
	}

	private void setOpenPrice(Tick lastTick, Tick newTick, BigDecimal price) {
		BigDecimal close = lastTick.getClose();
		if (close == null) {
			newTick.setOpen(price);
		} else {
			newTick.setOpen(close);
		}
	}

	private void handle(TickDao dao, Tick tick, TickGranularity granularity, Tick lastTick) {
		Instant lastTickInstant = lastTick.getCreateTime().toInstant();
		Instant tickInstant = granularity.truncate(tick.getCreateTime().toInstant());
		
		if (lastTickInstant.equals(tickInstant)) {
			lastTick.update(tick);
			dao.updateTick(lastTick);
			Events.TICK_CHANGED.fire(lastTick.clone());
			if (granularity == DefaultTickGranularity.Minute) {
				this.last24HoursTickerWindow.update(tick);
			}
		} else {
			Tick newTick = tick.clone();
			setOpenPrice(lastTick, newTick, tick.getOpen());
			newTick.setGranularity(granularity.getValue());
			newTick.setCreateTime(Timestamp.from(tickInstant));
			newTick.setUpdateTime(newTick.getCreateTime());
			dao.addTick(newTick);
			this.lastTicks.replace(granularity, lastTick, newTick);
			Events.TICK_CHANGED.fire(newTick.clone());
			if (granularity == DefaultTickGranularity.Minute) {
				this.last24HoursTickerWindow.move(newTick);
			}
		}
	}
	
	public Tick getLast24HoursTick() {
		return this.last24HoursTickerWindow.getAggregate();
	}
}
