package conflux.dex.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.codahale.metrics.annotation.Timed;

import conflux.dex.common.BusinessFault;
import conflux.dex.model.CancelOrderRequest;
import conflux.dex.model.Order;
import conflux.dex.model.OrderFilter;
import conflux.dex.model.OrderPruneRecord;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.PagingResult;
import conflux.dex.model.SettlementStatus;

public interface OrderDao {

	boolean addOrder(Order order);
	default void mustAddOrder(Order order) {
		if (!this.addOrder(order)) {
			throw BusinessFault.RecordAlreadyExists.rise();
		}
	}

	List<Order> listOrdersByStatus(long userId, int productId, OrderStatus status, int offset, int limit, boolean asc);
	List<Order> listOrdersByStatus(long userId, OrderStatus status, int offset, int limit, boolean asc);
	List<Order> listOrdersByTimeRange(long userId, int productId, Timestamp start, Timestamp end, int offset, int limit, boolean asc);
	List<Order> listOrdersByTimeRange(long userId, Timestamp start, Timestamp end, int offset, int limit, boolean asc);

	PagingResult<Order> listOrdersByPhase(long userId, int productId, OrderFilter.Phase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc);
	PagingResult<Order> listOrdersByPhase(long userId, OrderFilter.Phase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc);
	PagingResult<Order> listOrdersBySidedPhase(long userId, int productId, OrderFilter.SidedPhase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc);
	PagingResult<Order> listOrdersBySidedPhase(long userId, OrderFilter.SidedPhase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc);

	boolean updateOrderStatus(long orderId, OrderStatus oldStatus, OrderStatus newStatus);

	void fillOrder(long orderId, BigDecimal amount, BigDecimal funds);

	@Timed(name = "conflux.dex.dao.OrderDao.get.id", absolute = true)
	Optional<Order> getOrder(long orderId);

	@Timed(name = "conflux.dex.dao.OrderDao.getForUpdate.id", absolute = true)
	Optional<Order> getOrderForUpdate(long orderId);

	@Timed(name = "conflux.dex.dao.OrderDao.get.id", absolute = true)
	default Order mustGetOrder(long orderId) {
		Optional<Order> order = this.getOrder(orderId);
		if (!order.isPresent()) {
			throw BusinessFault.OrderNotFound.rise();
		}

		return order.get();
	}

	@Timed(name = "conflux.dex.dao.OrderDao.getForUpdate.id", absolute = true)
	default Order mustGetOrderForUpdate(long orderId) {
		Optional<Order> order = this.getOrderForUpdate(orderId);
		if (!order.isPresent()) {
			throw BusinessFault.OrderNotFound.rise();
		}

		return order.get();
	}

	@Timed(name = "conflux.dex.dao.OrderDao.get.uid.coid", absolute = true)
	Optional<Order> getOrderByClientOrderId(long userId, String clientOrderId);
	@Timed(name = "conflux.dex.dao.OrderDao.get.uid.coid", absolute = true)
	default Order mustGetOrderByClientOrderId(long userId, String clientOrderId) {
		Optional<Order> order = this.getOrderByClientOrderId(userId, clientOrderId);
		if (!order.isPresent()) {
			throw BusinessFault.OrderNotFound.rise();
		}

		return order.get();
	}

	Optional<Long> getOrderByHash(String hash);

	List<Order> listAllOrdersByStatus(OrderStatus status);
	List<Order> listAllOrdersByStatus(int productId, OrderStatus status);

	boolean addCancelOrderRequest(CancelOrderRequest request);
	default void mustAddCancelOrderRequest(CancelOrderRequest request) {
		if (!this.addCancelOrderRequest(request)) {
			throw BusinessFault.RecordAlreadyExists.rise();
		}
	}

	void updateCancelOrderRequest(long orderId, SettlementStatus status, String txHash, long txNonce);
	Optional<CancelOrderRequest> getCancelOrderRequest(long orderId);

	default CancelOrderRequest mustGetCancelOrderRequest(long orderId) {
		Optional<CancelOrderRequest> request = this.getCancelOrderRequest(orderId);
		if (!request.isPresent()) {
			throw BusinessFault.OrderNotFound.rise();
		}

		return request.get();
	}

	boolean deleteCancelOrderRequest(long orderId);

	List<CancelOrderRequest> listCancelOrderRequests(SettlementStatus status);

	default boolean addOrderPruneRecord(long timestamp, long orderId) { return false; };
	default void deleteOrderPruneRecord(long timestamp, long orderId) { };
	default List<OrderPruneRecord> getOrderPruneRecords(long timestampUpperBoundExclusive, int offset, int limit) { return null; };
	static void fillStatement(PreparedStatement ps, Order order) throws SQLException {
		ps.setInt(1, order.getProductId());
		ps.setLong(2, order.getUserId());
		ps.setString(3, order.getClientOrderId());
		ps.setString(4, order.getType().name());
		ps.setString(5, order.getSide().name());
		ps.setString(6, order.getStatus().name());
		ps.setByte(7, OrderFilter.Phase.parse(order.getStatus()).getValue());
		ps.setByte(8, OrderFilter.SidedPhase.parse(order).getValue());
		ps.setBigDecimal(9, order.getPrice());
		ps.setBigDecimal(10, order.getAmount());
		ps.setString(11, order.getFeeAddress());
		ps.setDouble(12, order.getFeeRateTaker());
		ps.setDouble(13, order.getFeeRateMaker());
		ps.setBigDecimal(14, order.getFilledAmount());
		ps.setBigDecimal(15, order.getFilledFunds());
		ps.setLong(16, order.getTimestamp());
		ps.setString(17, order.getHash());
		ps.setString(18, order.getSignature());
		ps.setTimestamp(19, order.getCreateTime());
		ps.setTimestamp(20, order.getUpdateTime());
		ps.setLong(21, order.getId());
	}
}

class InMemoryOrderDao extends IdAllocator implements OrderDao {
	private ConcurrentNavigableMap<Long, Order> items = new ConcurrentSkipListMap<Long, Order>();
	private ConcurrentMap<Long, CancelOrderRequest> cancelled = new ConcurrentHashMap<Long, CancelOrderRequest>();

	@Override
	public boolean addOrder(Order order) {
		if (order.getClientOrderId() != null
				&& this.items.values().stream().anyMatch(o -> order.getClientOrderId().equals(o.getClientOrderId()))) {
			return false;
		}

		order.setId(this.getNextId());
		order.setCreateTime(Timestamp.from(Instant.now()));
		order.setUpdateTime(order.getCreateTime());

		this.items.put(order.getId(), order);

		return true;
	}

	@Override
	public List<Order> listOrdersByStatus(long userId, int productId, OrderStatus status, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();

		return keys.stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getUserId() == userId && o.getProductId() == productId && o.getStatus().equals(status))
				.skip(offset)
				.limit(limit)
				.collect(Collectors.toList());
	}

	@Override
	public List<Order> listOrdersByStatus(long userId, OrderStatus status, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();

		return keys.stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getUserId() == userId && o.getStatus().equals(status))
				.skip(offset)
				.limit(limit)
				.collect(Collectors.toList());
	}

	@Override
	public List<Order> listOrdersByTimeRange(long userId, int productId, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();

		return keys.stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getUserId() == userId
					&& o.getProductId() == productId
					&& o.getCreateTime().after(start)
					&& o.getCreateTime().before(end))
				.skip(offset)
				.limit(limit)
				.collect(Collectors.toList());
	}

	@Override
	public List<Order> listOrdersByTimeRange(long userId, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();

		return keys.stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getUserId() == userId
					&& o.getCreateTime().after(start)
					&& o.getCreateTime().before(end))
				.skip(offset)
				.limit(limit)
				.collect(Collectors.toList());
	}

	@Override
	public PagingResult<Order> listOrdersByPhase(long userId, int productId, OrderFilter.Phase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();

		List<Order> orders = keys.stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getUserId() == userId
					&& o.getProductId() == productId
					&& OrderFilter.Phase.parse(o.getStatus()).equals(filter)
					&& o.getCreateTime().after(start)
					&& o.getCreateTime().before(end))
				.collect(Collectors.toList());

		return PagingResult.fromList(offset, limit, orders);
	}

	@Override
	public PagingResult<Order> listOrdersByPhase(long userId, OrderFilter.Phase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();

		List<Order> orders = keys.stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getUserId() == userId
					&& OrderFilter.Phase.parse(o.getStatus()).equals(filter)
					&& o.getCreateTime().after(start)
					&& o.getCreateTime().before(end))
				.collect(Collectors.toList());

		return PagingResult.fromList(offset, limit, orders);
	}

	@Override
	public PagingResult<Order> listOrdersBySidedPhase(long userId, int productId, OrderFilter.SidedPhase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();

		List<Order> orders = keys.stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getUserId() == userId
					&& o.getProductId() == productId
					&& OrderFilter.SidedPhase.parse(o).equals(filter)
					&& o.getCreateTime().after(start)
					&& o.getCreateTime().before(end))
				.collect(Collectors.toList());

		return PagingResult.fromList(offset, limit, orders);
	}

	@Override
	public PagingResult<Order> listOrdersBySidedPhase(long userId, OrderFilter.SidedPhase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();

		List<Order> orders = keys.stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getUserId() == userId
					&& OrderFilter.SidedPhase.parse(o).equals(filter)
					&& o.getCreateTime().after(start)
					&& o.getCreateTime().before(end))
				.collect(Collectors.toList());

		return PagingResult.fromList(offset, limit, orders);
	}

	@Override
	public boolean updateOrderStatus(long orderId, OrderStatus oldStatus, OrderStatus newStatus) {
		Order order = this.items.get(orderId);
		if (order == null) {
			throw BusinessFault.OrderNotFound.rise();
		}

		if (order.getStatus() != oldStatus) {
			return false;
		}

		order.setStatus(newStatus);
		order.setUpdateTime(Timestamp.from(Instant.now()));

		return true;
	}

	@Override
	public void fillOrder(long orderId, BigDecimal amount, BigDecimal funds) {
		Order order = this.items.get(orderId);
		order.setFilledAmount(order.getFilledAmount().add(amount));
		order.setFilledFunds(order.getFilledFunds().add(funds));
		order.setUpdateTime(Timestamp.from(Instant.now()));
	}

	@Override
	public Optional<Order> getOrder(long orderId) {
		return Optional.ofNullable(this.items.get(orderId));
	}

	@Override
	public Optional<Order> getOrderForUpdate(long orderId) {
		return Optional.ofNullable(this.items.get(orderId));
	}

	@Override
	public Optional<Order> getOrderByClientOrderId(long userId, String clientOrderId) {
		return this.items.navigableKeySet().stream()
				.map(id -> this.items.get(id))
				.filter(o -> clientOrderId.equals(o.getClientOrderId()))
				.findAny();
	}

	@Override
	public Optional<Long> getOrderByHash(String hash) {
		return this.items.navigableKeySet().stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getHash().equalsIgnoreCase(hash))
				.map(o -> o.getId())
				.findAny();
	}

	@Override
	public List<Order> listAllOrdersByStatus(OrderStatus status) {
		return this.items.navigableKeySet().stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getStatus().equals(status))
				.collect(Collectors.toList());
	}

	@Override
	public List<Order> listAllOrdersByStatus(int productId, OrderStatus status) {
		return this.items.navigableKeySet().stream()
				.map(id -> this.items.get(id))
				.filter(o -> o.getStatus().equals(status))
				.filter(o -> (o.getProductId() == productId))
				.collect(Collectors.toList());
	}


	@Override
	public boolean addCancelOrderRequest(CancelOrderRequest request) {
		return this.cancelled.putIfAbsent(request.getOrderId(), request) == null;
	}

	@Override
	public void updateCancelOrderRequest(long orderId, SettlementStatus status, String txHash, long txNonce) {
		CancelOrderRequest request = this.cancelled.get(orderId);
		if (request != null) {
			request.setStatus(status);

			if (!StringUtils.isEmpty(txHash)) {
				request.setTxHash(txHash);
				request.setTxNonce(txNonce);
			}
		}
	}

	@Override
	public Optional<CancelOrderRequest> getCancelOrderRequest(long orderId) {
		return Optional.ofNullable(this.cancelled.get(orderId));
	}

	@Override
	public boolean deleteCancelOrderRequest(long orderId) {
		return this.cancelled.remove(orderId) != null;
	}

	@Override
	public List<CancelOrderRequest> listCancelOrderRequests(SettlementStatus status) {
		return this.cancelled.values().stream()
				.filter(request -> request.getStatus().equals(status))
				.collect(Collectors.toList());
	}
}

class TimeIndex implements Comparable<TimeIndex> {
	public Timestamp timestamp;
	public long id;

	public TimeIndex(Timestamp timestamp, long id) {
		this.timestamp = timestamp;
		this.id = id;
	}

	@Override
	public int compareTo(TimeIndex o) {
		int cmp = this.timestamp.compareTo(o.timestamp);
		if (cmp != 0) {
			return cmp;
		}

		return Long.compare(this.id, o.id);
	}

	@Override
	public int hashCode() {
		return String.format("%d-%d", this.timestamp.getTime(), this.id).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TimeIndex)) {
			return false;
		}

		return this.compareTo((TimeIndex) obj) == 0;
	}
}

@Repository
class OrderDaoImpl extends BaseDaoImpl implements OrderDao {
	private static final RowMapper<Order> orderRowMapper = BeanPropertyRowMapper.newInstance(Order.class);
	private static final RowMapper<CancelOrderRequest> cancelRowMapper = BeanPropertyRowMapper.newInstance(CancelOrderRequest.class);
	private static final RowMapper<OrderPruneRecord> pruneRowMapper = BeanPropertyRowMapper.newInstance(OrderPruneRecord.class);

	public static final String SQL_INSERT = String.join(" ",
			"INSERT INTO t_order",
			"(product_id, user_id, client_order_id, type, side, status, phase, phase_side, price, amount, fee_address, fee_rate_taker, fee_rate_maker, filled_amount, filled_funds, timestamp, hash, signature, create_time, update_time, id)",
			"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

	@Override
	public boolean addOrder(Order order) {
		Timestamp now = Timestamp.from(Instant.now());
		order.setCreateTime(now);
		order.setUpdateTime(now);

		PreparedStatementCreator creator = new PreparedStatementCreator() {

			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
				OrderDao.fillStatement(ps, order);
				return ps;
			}
		};

		KeyHolder keyHolder = new GeneratedKeyHolder();
		if (this.getJdbcTemplate().update(creator, keyHolder) > 0) {
			order.setId(keyHolder.getKey().longValue());
			return true;
		} else {
			return false;
		}
	}

	@Override
	@Timed(name = "list.uid.pid.status")
	public List<Order> listOrdersByStatus(long userId, int productId, OrderStatus status, int offset, int limit, boolean asc) {
		String sql;
		if (asc) {
			sql = "SELECT * FROM t_order WHERE user_id = ? AND product_id = ? AND status = ? ORDER BY id LIMIT ?,?";
		} else {
			sql = "SELECT * FROM t_order WHERE user_id = ? AND product_id = ? AND status = ? ORDER BY id DESC LIMIT ?,?";
		}

		return this.getJdbcTemplate().query(sql, orderRowMapper, userId, productId, status.name(), offset, limit);
	}

	@Override
	@Timed(name = "list.uid.status")
	public List<Order> listOrdersByStatus(long userId, OrderStatus status, int offset, int limit, boolean asc) {
		String sql;
		if (asc) {
			sql = "SELECT * FROM t_order WHERE user_id = ? AND status = ? ORDER BY id LIMIT ?,?";
		} else {
			sql = "SELECT * FROM t_order WHERE user_id = ? AND status = ? ORDER BY id DESC LIMIT ?,?";
		}

		return this.getJdbcTemplate().query(sql, orderRowMapper, userId, status.name(), offset, limit);
	}

	@Override
	@Timed(name = "list.uid.pid.time")
	public List<Order> listOrdersByTimeRange(long userId, int productId, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		String sql;
		if (asc) {
			sql = "SELECT * FROM t_order WHERE user_id = ? AND product_id = ? AND create_time BETWEEN ? AND ? ORDER BY create_time, id LIMIT ?,?";
		} else {
			sql = "SELECT * FROM t_order WHERE user_id = ? AND product_id = ? AND create_time BETWEEN ? AND ? ORDER BY create_time DESC, id DESC LIMIT ?,?";
		}

		return this.getJdbcTemplate().query(sql, orderRowMapper, userId, productId, start, end, offset, limit);
	}

	@Override
	@Timed(name = "list.uid.time")
	public List<Order> listOrdersByTimeRange(long userId, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		String sql;
		if (asc) {
			sql = "SELECT * FROM t_order WHERE user_id = ? AND create_time BETWEEN ? AND ? ORDER BY create_time, id LIMIT ?,?";
		} else {
			sql = "SELECT * FROM t_order WHERE user_id = ? AND create_time BETWEEN ? AND ? ORDER BY create_time DESC, id DESC LIMIT ?,?";
		}

		return this.getJdbcTemplate().query(sql, orderRowMapper, userId, start, end, offset, limit);
	}

	@Override
	@Timed(name = "list.uid.pid.phase.time")
	public PagingResult<Order> listOrdersByPhase(long userId, int productId, OrderFilter.Phase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_order WHERE user_id = ? AND product_id = ? AND phase = ? AND create_time BETWEEN ? AND ? ORDER BY create_time, id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_order WHERE user_id = ? AND product_id = ? AND phase = ? AND create_time BETWEEN ? AND ? ORDER BY create_time DESC, id DESC LIMIT ?,?";
		}

		List<Order> orders = this.getJdbcTemplate().query(listSql, orderRowMapper, userId, productId, filter.getValue(), start, end, offset, limit);

		String totalSql = "SELECT COUNT(id) FROM t_order WHERE user_id = ? AND product_id = ? AND phase = ? AND create_time BETWEEN ? AND ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userId, productId, filter.getValue(), start, end);

		return new PagingResult<Order>(offset, limit, orders, total);
	}

	@Override
	@Timed(name = "list.uid.phase.time")
	public PagingResult<Order> listOrdersByPhase(long userId, OrderFilter.Phase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_order WHERE user_id = ? AND phase = ? AND create_time BETWEEN ? AND ? ORDER BY create_time, id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_order WHERE user_id = ? AND phase = ? AND create_time BETWEEN ? AND ? ORDER BY create_time DESC, id DESC LIMIT ?,?";
		}

		List<Order> orders = this.getJdbcTemplate().query(listSql, orderRowMapper, userId, filter.getValue(), start, end, offset, limit);

		String totalSql = "SELECT COUNT(id) FROM t_order WHERE user_id = ? AND phase = ? AND create_time BETWEEN ? AND ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userId, filter.getValue(), start, end);

		return new PagingResult<Order>(offset, limit, orders, total);
	}

	@Override
	@Timed(name = "list.uid.pid.sidedphase.time")
	public PagingResult<Order> listOrdersBySidedPhase(long userId, int productId, OrderFilter.SidedPhase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_order WHERE user_id = ? AND product_id = ? AND phase_side = ? AND create_time BETWEEN ? AND ? ORDER BY create_time, id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_order WHERE user_id = ? AND product_id = ? AND phase_side = ? AND create_time BETWEEN ? AND ? ORDER BY create_time DESC, id DESC LIMIT ?,?";
		}

		List<Order> orders = this.getJdbcTemplate().query(listSql, orderRowMapper, userId, productId, filter.getValue(), start, end, offset, limit);

		String totalSql = "SELECT COUNT(id) FROM t_order WHERE user_id = ? AND product_id = ? AND phase_side = ? AND create_time BETWEEN ? AND ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userId, productId, filter.getValue(), start, end);

		return new PagingResult<Order>(offset, limit, orders, total);
	}

	@Override
	@Timed(name = "list.uid.sidedphase.time")
	public PagingResult<Order> listOrdersBySidedPhase(long userId, OrderFilter.SidedPhase filter, Timestamp start, Timestamp end, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_order WHERE user_id = ? AND phase_side = ? AND create_time BETWEEN ? AND ? ORDER BY create_time, id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_order WHERE user_id = ? AND phase_side = ? AND create_time BETWEEN ? AND ? ORDER BY create_time DESC, id DESC LIMIT ?,?";
		}

		List<Order> orders = this.getJdbcTemplate().query(listSql, orderRowMapper, userId, filter.getValue(), start, end, offset, limit);

		String totalSql = "SELECT COUNT(id) FROM t_order WHERE user_id = ? AND phase_side = ? AND create_time BETWEEN ? AND ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userId, filter.getValue(), start, end);

		return new PagingResult<Order>(offset, limit, orders, total);
	}

	@Override
	@Timed(name = "update.status")
	public boolean updateOrderStatus(long orderId, OrderStatus oldStatus, OrderStatus newStatus) {
		String sql = "UPDATE t_order SET status = ?, phase = phase + ?, phase_side = phase_side + ?, update_time = UTC_TIMESTAMP() WHERE id = ? AND status = ?";
		byte deltaPhase = OrderFilter.Phase.getDelta(oldStatus, newStatus);
		byte deltaSidedPhase = OrderFilter.SidedPhase.getDelta(oldStatus, newStatus);
		return this.getJdbcTemplate().update(sql, newStatus.name(), deltaPhase, deltaSidedPhase, orderId, oldStatus.name()) > 0;
	}

	@Override
	@Timed(name = "update.filled")
	public void fillOrder(long orderId, BigDecimal amount, BigDecimal funds) {
		String sql = "UPDATE t_order SET filled_amount = filled_amount + ?, filled_funds = filled_funds + ?, update_time = UTC_TIMESTAMP() WHERE id = ?";
		this.getJdbcTemplate().update(sql, amount, funds, orderId);
	}

	@Override
	public Optional<Order> getOrder(long orderId) {
		String sql = "SELECT * FROM t_order WHERE id = ?";
		List<Order> orders = this.getJdbcTemplate().query(sql, orderRowMapper, orderId);
		return orders.isEmpty() ? Optional.empty() : Optional.of(orders.get(0));
	}
	
	@Override
	public Optional<Order> getOrderForUpdate(long orderId) {
		String sql = "SELECT * FROM t_order WHERE id = ? FOR UPDATE";
		List<Order> orders = this.getJdbcTemplate().query(sql, orderRowMapper, orderId);
		return orders.isEmpty() ? Optional.empty() : Optional.of(orders.get(0));
	}
	
	@Override
	public Optional<Order> getOrderByClientOrderId(long userId, String clientOrderId) {
		String sql = "SELECT * FROM t_order WHERE user_id = ? AND client_order_id = ?";
		List<Order> orders = this.getJdbcTemplate().query(sql, orderRowMapper, userId, clientOrderId);
		return orders.isEmpty() ? Optional.empty() : Optional.of(orders.get(0));
	}
	
	@Override
	@Timed(name = "get.hash")
	public Optional<Long> getOrderByHash(String hash) {
		String sql = "SELECT id FROM t_order WHERE hash = ?";
		List<Long> orders = this.getJdbcTemplate().queryForList(sql, Long.class, hash);
		return orders.isEmpty() ? Optional.empty() : Optional.of(orders.get(0));
	}
	
	@Override
	public List<Order> listAllOrdersByStatus(OrderStatus status) {
		// NOTE, do not query by status Cancelled or Filled.
		// Otherwise, the 'status' index may not take effect and cause full table scan.
		String sql = "SELECT * FROM t_order WHERE status = ? ORDER BY id";
		return this.getJdbcTemplate().query(sql, orderRowMapper, status.name());
	}
	
	@Override
	public List<Order> listAllOrdersByStatus(int productId, OrderStatus status) {
		// NOTE, do not query by status Cancelled or Filled.
		// Otherwise, the 'status' index may not take effect and cause full table scan.
		String sql = "SELECT * FROM t_order WHERE status = ? AND product_id = ? ORDER BY id";
		return this.getJdbcTemplate().query(sql, orderRowMapper, status.name(), productId);
	}
	
	@Override
	public boolean addCancelOrderRequest(CancelOrderRequest request) {
		String sql = "INSERT IGNORE INTO t_order_cancel (order_id, reason, timestamp, signature, status, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
		return this.getJdbcTemplate().update(sql, request.getOrderId(), request.getReason().name(), request.getTimestamp(), request.getSignature(), request.getStatus().name(), request.getCreateTime(), request.getUpdateTime()) > 0;
	}
	
	@Override
	public void updateCancelOrderRequest(long orderId, SettlementStatus status, String txHash, long txNonce) {
		if (StringUtils.isEmpty(txHash)) {
			String sql = "UPDATE t_order_cancel SET status = ?, update_time = UTC_TIMESTAMP() WHERE order_id = ?";
			this.getJdbcTemplate().update(sql, status.name(), orderId);
		} else {
			String sql = "UPDATE t_order_cancel SET status = ?, tx_hash = ?, tx_nonce = ?, update_time = UTC_TIMESTAMP() WHERE order_id = ?";
			this.getJdbcTemplate().update(sql, status.name(), txHash, txNonce, orderId);
		}
	}
	
	@Override
	public Optional<CancelOrderRequest> getCancelOrderRequest(long orderId) {
		String sql = "SELECT * FROM t_order_cancel WHERE order_id = ?";
		List<CancelOrderRequest> requests = this.getJdbcTemplate().query(sql, cancelRowMapper, orderId);
		return requests.isEmpty() ? Optional.empty() : Optional.of(requests.get(0));
	}
	
	@Override
	public boolean deleteCancelOrderRequest(long orderId) {
		String sql = "DELETE FROM t_order_cancel WHERE order_id = ?";
		return this.getJdbcTemplate().update(sql, orderId) > 0;
	}
	
	@Override
	public List<CancelOrderRequest> listCancelOrderRequests(SettlementStatus status) {
		// NOTE, do not query by status OnChainConfirmed.
		// Otherwise, the 'status' index may not take effect and cause full table scan.
		String sql = "SELECT * FROM t_order_cancel WHERE status = ? ORDER BY id";
		return this.getJdbcTemplate().query(sql, cancelRowMapper, status.name());
	}
	
	@Override
	public boolean addOrderPruneRecord(long timestamp, long orderId) {
		String sql = "INSERT IGNORE INTO t_order_prune (timestamp, order_id) VALUES (?, ?)";
		return this.getJdbcTemplate().update(sql, timestamp, orderId) > 0;
	}
	
	@Override
	public void deleteOrderPruneRecord(long timestamp, long orderId) {
		String sql = "DELETE FROM t_order_prune WHERE timestamp = ? AND order_id = ?";
		this.getJdbcTemplate().update(sql, timestamp, orderId);
	}
	
	private static final String SQL_GET_PRUNE_ORDERS = String.join(" ",
			"SELECT p.timestamp, p.order_id, o.hash",
			"FROM t_order_prune p",
			"INNER JOIN t_order o ON p.order_id = o.id",
			"WHERE p.timestamp < ?",
			"ORDER BY p.timestamp, p.order_id",
			"LIMIT ?,?");
	
	@Override
	public List<OrderPruneRecord> getOrderPruneRecords(long timestampUpperBoundExclusive, int offset, int limit) {
		return this.getJdbcTemplate().query(SQL_GET_PRUNE_ORDERS, pruneRowMapper, timestampUpperBoundExclusive, offset, limit);
	}
	
}
