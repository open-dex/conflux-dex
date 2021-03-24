package conflux.dex.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedList;
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
import org.springframework.util.StringUtils;

import conflux.dex.model.PagingResult;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.Trade;

public interface TradeDao {
	
	void addTrade(Trade trade);
	
	void addTradeOrderMap(long orderId, long tradeId);
	
	void addTradeUserMap(long userId, int productId, Timestamp createTime, long tradeId);
	
	List<Trade> listRecentTrades(int productId, int offset, int limit);
	
	Optional<Trade> getRecentTradeBefore(int productId, Timestamp timestamp);
	
	List<Trade> listTradesByOrderId(long orderId, int offset, int limit);
	
	List<Trade> listTradesByUser(long userId, int productId, Timestamp start, Timestamp end, int offset, int limit, boolean asc);
	
	void updateTradeSettlement(long tradeId, SettlementStatus status, String txHash, long txNonce);
	
	List<Trade> listTradesByStatus(SettlementStatus status);

	Trade getById(long id);

	static void fillStatement(PreparedStatement ps, Trade trade) throws SQLException {
		ps.setInt(1, trade.getProductId());
		ps.setLong(2, trade.getTakerOrderId());
		ps.setLong(3, trade.getMakerOrderId());
		ps.setBigDecimal(4, trade.getPrice());
		ps.setBigDecimal(5, trade.getAmount());
		ps.setString(6, trade.getSide().name());
		ps.setBigDecimal(7, trade.getTakerFee());
		ps.setBigDecimal(8, trade.getMakerFee());
		ps.setString(9, trade.getStatus().name());
		ps.setTimestamp(10, trade.getCreateTime());
		ps.setTimestamp(11, trade.getUpdateTime());
		ps.setLong(12, trade.getId());
	}
}

class InMemoryTradeDao extends IdAllocator implements TradeDao {
	private NavigableMap<Long, Trade> items = new ConcurrentSkipListMap<Long, Trade>();
	private Map<Integer, List<Trade>> productIndex = new ConcurrentHashMap<Integer, List<Trade>>();
	private Map<Long, List<Trade>> orderIndex = new ConcurrentHashMap<Long, List<Trade>>();
	private Map<Long, Map<Integer, NavigableMap<TimeIndex, Trade>>> userIndex = new ConcurrentHashMap<Long, Map<Integer, NavigableMap<TimeIndex, Trade>>>();
	
	@Override
	public void addTrade(Trade trade) {
		trade.setId(this.getNextId());
		this.items.put(trade.getId(), trade);
		
		// update product index
		this.productIndex.putIfAbsent(trade.getProductId(), Collections.synchronizedList(new LinkedList<Trade>()));
		this.productIndex.get(trade.getProductId()).add(0, trade);
	}
	
	@Override
	public void addTradeOrderMap(long orderId, long tradeId) {
		this.orderIndex.putIfAbsent(orderId, Collections.synchronizedList(new LinkedList<Trade>()));
		this.orderIndex.get(orderId).add(this.items.get(tradeId));
	}
	
	@Override
	public void addTradeUserMap(long userId, int productId, Timestamp createTime, long tradeId) {
		this.userIndex.putIfAbsent(userId, new ConcurrentHashMap<Integer, NavigableMap<TimeIndex, Trade>>());
		this.userIndex.get(userId).putIfAbsent(productId, new ConcurrentSkipListMap<TimeIndex, Trade>());
		this.userIndex.get(userId).get(productId).put(new TimeIndex(createTime, tradeId), this.items.get(tradeId));
	}

	@Override
	public List<Trade> listRecentTrades(int productId, int offset, int limit) {
		List<Trade> trades = this.productIndex.get(productId);
		if (trades == null) {
			return Collections.emptyList();
		}
		
		return PagingResult.fromList(offset, limit, trades).getItems();
	}
	
	@Override
	public Optional<Trade> getRecentTradeBefore(int productId, Timestamp timestamp) {
		List<Trade> trades = this.productIndex.get(productId);
		if (trades == null) {
			return Optional.empty();
		}
		int l = 0, r = trades.size() - 1;
		while (l <= r) {
			int mid = (l + r) >> 1;
			if (trades.get(mid).getCreateTime().after(timestamp))
				l = mid + 1;
			else 
				r = mid - 1;
		}
		int idx = r + 1;
		if (idx >= 0 && idx < trades.size())
			return Optional.of(trades.get(idx));
		return Optional.empty();
	}

	@Override
	public List<Trade> listTradesByOrderId(long orderId, int offset, int limit) {
		List<Trade> trades = this.orderIndex.get(orderId);
		if (trades == null) {
			return Collections.emptyList();
		}
		
		return PagingResult.fromList(offset, limit, trades).getItems();
	}

	@Override
	public List<Trade> listTradesByUser(long userId, int productId, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		Map<Integer, NavigableMap<TimeIndex, Trade>> productIndex = this.userIndex.get(userId);
		if (productIndex == null) {
			return Collections.emptyList();
		}
		
		NavigableMap<TimeIndex, Trade> timeIndex = productIndex.get(productId);
		if (timeIndex == null) {
			return Collections.emptyList();
		}
		
		TimeIndex fromKey = new TimeIndex(start, 0);
		TimeIndex toKey = new TimeIndex(end, Long.MAX_VALUE);
		timeIndex = timeIndex.subMap(fromKey, true, toKey, true);
		
		if (!asc) {
			timeIndex = timeIndex.descendingMap();
		}
		
		return timeIndex.values().stream()
				.skip(offset)
				.limit(limit)
				.collect(Collectors.toList());
	}
	
	@Override
	public void updateTradeSettlement(long tradeId, SettlementStatus status, String txHash, long txNonce) {
		Trade trade = this.items.get(tradeId);
		if (trade != null) {
			trade.setStatus(status);
			
			if (!StringUtils.isEmpty(txHash)) {
				trade.setTxHash(txHash);
				trade.setTxNonce(txNonce);
			}
		}
	}
	
	@Override
	public List<Trade> listTradesByStatus(SettlementStatus status) {
		return this.items.navigableKeySet().stream()
				.filter(id -> this.items.get(id).getStatus().equals(status))
				.map(id -> this.items.get(id))
				.collect(Collectors.toList());
	}

	public Trade getById(long id) {
		return this.items.get(id);
	}
}

@Repository
class TradeDaoImpl extends BaseDaoImpl implements TradeDao {
	private static final RowMapper<Trade> rowMapper = BeanPropertyRowMapper.newInstance(Trade.class);
	
	public static final String SQL_INSERT = String.join(" ",
			"INSERT INTO t_trade", // do not change table name
			"(product_id, taker_order_id, maker_order_id, price, amount, side, taker_fee, maker_fee, status, create_time, update_time, id)",
			"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
	
	private static final String SQL_LIST_BY_ORDER = String.join(" ",
			"SELECT t_trade.*",
			"FROM t_trade",
			"INNER JOIN t_trade_order_map AS m ON t_trade.id = m.trade_id AND m.order_id = ?",
			"ORDER BY t_trade.id",
			"LIMIT ?,?");
	
	private static final String SQL_LIST_BY_USER = String.join(" ",
			"SELECT t_trade.*",
			"FROM t_trade",
			"INNER JOIN t_trade_user_map AS m ON t_trade.id = m.trade_id AND m.user_id = ? AND m.product_id = ? AND m.create_time BETWEEN ? AND ?",
			"ORDER BY t_trade.id",
			"LIMIT ?,?");
	
	private static final String SQL_LIST_BY_USER_DESC = String.join(" ", 
			"SELECT t_trade.*",
			"FROM t_trade",
			"INNER JOIN t_trade_user_map AS m ON t_trade.id = m.trade_id AND m.user_id = ? AND m.product_id = ? AND m.create_time BETWEEN ? AND ?",
			"ORDER BY t_trade.id DESC",
			"LIMIT ?,?");

	@Override
	public void addTrade(Trade trade) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {
			
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
				TradeDao.fillStatement(ps, trade);
				return ps;
			}
		};
		
		KeyHolder keyHolder = new GeneratedKeyHolder();
		this.getJdbcTemplate().update(creator, keyHolder);
		trade.setId(keyHolder.getKey().longValue());
	}
	
	@Override
	public void addTradeOrderMap(long orderId, long tradeId) {
		String sql = "INSERT INTO t_trade_order_map (order_id, trade_id) VALUES (?, ?)";
		this.getJdbcTemplate().update(sql, orderId, tradeId);
	}
	
	@Override
	public void addTradeUserMap(long userId, int productId, Timestamp createTime, long tradeId) {
		String sql = "INSERT INTO t_trade_user_map (user_id, product_id, create_time, trade_id) VALUES (?, ?, ?, ?)";
		this.getJdbcTemplate().update(sql, userId, productId, createTime, tradeId);
	}

	@Override
	public List<Trade> listRecentTrades(int productId, int offset, int limit) {
		String sql = "SELECT * FROM t_trade WHERE product_id = ? ORDER BY id DESC LIMIT ?,?";
		return this.getJdbcTemplate().query(sql, rowMapper, productId, offset, limit);
	}
	
	@Override
	public Optional<Trade> getRecentTradeBefore(int productId, Timestamp timestamp) {
		String sql = "SELECT * FROM t_trade WHERE product_id = ? AND create_time < ? ORDER BY id DESC LIMIT 0,1";
		List<Trade> result = this.getJdbcTemplate().query(sql, rowMapper, productId, timestamp);
		return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
	}

	@Override
	public List<Trade> listTradesByOrderId(long orderId, int offset, int limit) {
		return this.getJdbcTemplate().query(SQL_LIST_BY_ORDER, rowMapper, orderId, offset, limit);
	}

	@Override
	public List<Trade> listTradesByUser(long userId, int productId, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		String sql = asc ? SQL_LIST_BY_USER : SQL_LIST_BY_USER_DESC;
		return this.getJdbcTemplate().query(sql, rowMapper, userId, productId, start, end, offset, limit);
	}
	
	@Override
	public void updateTradeSettlement(long tradeId, SettlementStatus status, String txHash, long txNonce) {
		if (StringUtils.isEmpty(txHash)) {
			String sql = "UPDATE t_trade SET status = ?, update_time = UTC_TIMESTAMP() WHERE id = ?";
			this.getJdbcTemplate().update(sql, status.name(), tradeId);
		} else {
			String sql = "UPDATE t_trade SET status = ?, tx_hash = ?, tx_nonce = ?, update_time = UTC_TIMESTAMP() WHERE id = ?";
			this.getJdbcTemplate().update(sql, status.name(), txHash, txNonce, tradeId);
		}
	}
	
	@Override
	public List<Trade> listTradesByStatus(SettlementStatus status) {
		// NOTE, do not query by status OnChainConfirmed.
		// Otherwise, the 'status' index may not take effect and cause full table scan.
		String sql = "SELECT * FROM t_trade WHERE status = ? ORDER BY id";
		return this.getJdbcTemplate().query(sql, rowMapper, status.name());
	}

	public Trade getById(long id) {
		String sql = "SELECT * FROM t_trade WHERE id = ?";
		return this.getJdbcTemplate().query(sql, rowMapper, id).stream().findAny().orElse(null);
	}
}

