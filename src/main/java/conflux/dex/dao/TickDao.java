package conflux.dex.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.codahale.metrics.annotation.Timed;

import conflux.dex.common.BusinessFault;
import conflux.dex.model.Tick;

public interface TickDao {
	
	void addTick(Tick tick);
	
	Optional<Tick> getLastTick(int productId, int granularity);
	
	default Tick mustGetLastTick(int productId, int granularity) {
		Optional<Tick> tick = this.getLastTick(productId, granularity);
		if (!tick.isPresent()) {
			throw BusinessFault.TickNotFound.rise();
		}
		
		return tick.get();
	}
	
	void updateTick(Tick tick);
	
	List<Tick> listTicks(int productId, int granularity, Timestamp start, Timestamp end);
	
	/**
	 * List ticks in creation time descending order.
	 */
	List<Tick> listTicks(int productId, int granularity, Timestamp end, int limit);
	
	default long getTradeCount(int productId, int granularity) { return 0; };

}

class InMemoryTickDao extends IdAllocator implements TickDao {
	private Map<Long, Tick> items = new ConcurrentHashMap<Long, Tick>();
	private Map<Integer, Map<Integer, NavigableMap<Instant, Tick>>> index = new ConcurrentHashMap<Integer, Map<Integer, NavigableMap<Instant, Tick>>>();

	@Override
	public void addTick(Tick tick) {
		this.index.putIfAbsent(tick.getProductId(), new ConcurrentHashMap<Integer, NavigableMap<Instant, Tick>>());
		this.index.get(tick.getProductId()).putIfAbsent(tick.getGranularity(), new ConcurrentSkipListMap<Instant, Tick>());
		NavigableMap<Instant, Tick> ticks = this.index.get(tick.getProductId()).get(tick.getGranularity());
		
		tick.setId(this.getNextId());
		
		this.items.put(tick.getId(), tick);
		ticks.put(tick.getCreateTime().toInstant(), tick);
	}

	@Override
	public Optional<Tick> getLastTick(int productId, int granularity) {
		Map<Integer, NavigableMap<Instant, Tick>> granularityIndex = this.index.get(productId);
		if (granularityIndex == null) {
			return Optional.empty();
		}
		
		NavigableMap<Instant, Tick> ticks = granularityIndex.get(granularity);
		if (ticks == null || ticks.isEmpty()) {
			return Optional.empty();
		}
		
		Map.Entry<Instant, Tick> last = ticks.lastEntry();
		return last == null ? Optional.empty() : Optional.of(last.getValue());
	}

	@Override
	public void updateTick(Tick other) {
		Tick tick = this.items.get(other.getId());
		if (tick == null) {
			throw BusinessFault.TickNotFound.rise();
		}
		
		tick.setHigh(other.getHigh());
		tick.setLow(other.getLow());
		tick.setClose(other.getClose());
		tick.setBaseCurrencyVolume(other.getBaseCurrencyVolume());
		tick.setQuoteCurrencyVolume(other.getQuoteCurrencyVolume());
		tick.setCount(other.getCount());
	}

	@Override
	public List<Tick> listTicks(int productId, int granularity, Timestamp start, Timestamp end) {
		Map<Integer, NavigableMap<Instant, Tick>> granularityIndex = this.index.get(productId);
		if (granularityIndex == null) {
			return Collections.emptyList();
		}
		
		NavigableMap<Instant, Tick> ticks = granularityIndex.get(granularity);
		if (ticks == null || ticks.isEmpty()) {
			return Collections.emptyList();
		}
		
		return ticks.subMap(start.toInstant(), true, end.toInstant(), true)
				.navigableKeySet()
				.stream()
				.map(time -> ticks.get(time))
				.collect(Collectors.toList());
	}

	@Override
	public List<Tick> listTicks(int productId, int granularity, Timestamp end, int limit) {
		Map<Integer, NavigableMap<Instant, Tick>> granularityIndex = this.index.get(productId);
		if (granularityIndex == null) {
			return Collections.emptyList();
		}
		
		NavigableMap<Instant, Tick> ticks = granularityIndex.get(granularity);
		if (ticks == null || ticks.isEmpty()) {
			return Collections.emptyList();
		}
		
		List<Tick> result = ticks.headMap(end.toInstant(), false)
				.descendingKeySet()
				.stream()
				.limit(limit)
				.map(time -> ticks.get(time))
				.collect(Collectors.toList());
		
		return result;
	}
}

@Repository
class TickDaoImpl extends BaseDaoImpl implements TickDao {
	private static final RowMapper<Tick> tickRowMapper = BeanPropertyRowMapper.newInstance(Tick.class);
	
	private static final String SQL_INSERT = String.join(" ",
			"INSERT INTO t_tick",
			"(product_id, granularity, open, high, low, close, base_currency_volume, quote_currency_volume, count, create_time, update_time)",
			"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

	@Override
	public void addTick(Tick tick) {
		tick.setUpdateTime(Timestamp.from(Instant.now()));
		
		PreparedStatementCreator creator = new PreparedStatementCreator() {
			
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
				ps.setInt(1, tick.getProductId());
				ps.setInt(2, tick.getGranularity());
				ps.setBigDecimal(3, tick.getOpen());
				ps.setBigDecimal(4, tick.getHigh());
				ps.setBigDecimal(5, tick.getLow());
				ps.setBigDecimal(6, tick.getClose());
				ps.setBigDecimal(7, tick.getBaseCurrencyVolume());
				ps.setBigDecimal(8, tick.getQuoteCurrencyVolume());
				ps.setInt(9, tick.getCount());
				ps.setTimestamp(10, tick.getCreateTime());
				ps.setTimestamp(11, tick.getUpdateTime());
				return ps;
			}
		};
		
		KeyHolder keyHolder = new GeneratedKeyHolder();
		this.getJdbcTemplate().update(creator, keyHolder);
		tick.setId(keyHolder.getKey().longValue());
	}

	@Override
	public Optional<Tick> getLastTick(int productId, int granularity) {
		String sql = "SELECT * FROM t_tick WHERE product_id = ? AND granularity = ? ORDER BY create_time DESC LIMIT 1";
		List<Tick> ticks = this.getJdbcTemplate().query(sql, tickRowMapper, productId, granularity);
		return ticks.isEmpty() ? Optional.empty() : Optional.of(ticks.get(0));
	}

	@Override
	@Timed(name = "update")
	public void updateTick(Tick tick) {
		String sql = "UPDATE t_tick SET high = ?, low = ?, close = ?, base_currency_volume = ?, quote_currency_volume = ?, count = ?, update_time = UTC_TIMESTAMP() WHERE id = ?";
		this.getJdbcTemplate().update(sql, tick.getHigh(), tick.getLow(), tick.getClose(), 
				tick.getBaseCurrencyVolume(), tick.getQuoteCurrencyVolume(), tick.getCount(), tick.getId());
	}

	@Override
	@Timed(name = "list.time")
	public List<Tick> listTicks(int productId, int granularity, Timestamp start, Timestamp end) {
		String sql = "SELECT * FROM t_tick WHERE product_id = ? AND granularity = ? AND create_time BETWEEN ? AND ? ORDER BY create_time";
		return this.getJdbcTemplate().query(sql, tickRowMapper, productId, granularity, start, end);
	}

	@Override
	@Timed(name = "list.limit")
	public List<Tick> listTicks(int productId, int granularity, Timestamp end, int limit) {
		String sql = "SELECT * FROM t_tick WHERE product_id = ? AND granularity = ? AND create_time < ? ORDER BY create_time DESC LIMIT ?";
		List<Tick> ticks = this.getJdbcTemplate().query(sql, tickRowMapper, productId, granularity, end, limit);
		return ticks;
	}
	
	@Override
	public long getTradeCount(int productId, int granularity) {
		String sql = "SELECT SUM(count) FROM t_tick WHERE product_id = ? AND granularity = ?";
		Long count = this.getJdbcTemplate().queryForObject(sql, Long.class, productId, granularity);
		return count == null ? 0 : count;
	}
	
}
