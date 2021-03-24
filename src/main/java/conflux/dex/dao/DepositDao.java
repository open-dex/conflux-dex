package conflux.dex.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import conflux.dex.model.DepositRecord;
import conflux.dex.model.PagingResult;

public interface DepositDao {
	
	void addDepositRecord(DepositRecord record);
	
	PagingResult<DepositRecord> listDepositRecords(String userAddress, String currency, int offset, int limit, boolean asc);
	PagingResult<DepositRecord> listDepositRecords(String userAddress, int offset, int limit, boolean asc);
	
	BigDecimal getDepositSum(String currency);
}

class InMemoryDepositDao extends IdAllocator implements DepositDao {
	private ConcurrentNavigableMap<Long, DepositRecord> items = new ConcurrentSkipListMap<Long, DepositRecord>();
	
	@Override
	public void addDepositRecord(DepositRecord record) {
		record.setId(this.getNextId());
		this.items.put(record.getId(), record);
	}
	
	@Override
	public PagingResult<DepositRecord> listDepositRecords(String userAddress, String currency, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();
		
		List<DepositRecord> records = keys.stream()
				.map(id -> this.items.get(id))
				.filter(record -> record.getUserAddress().equalsIgnoreCase(userAddress) 
						&& record.getCurrency().equalsIgnoreCase(currency))
				.collect(Collectors.toList());
		
		return PagingResult.fromList(offset, limit, records);
	}
	
	@Override
	public PagingResult<DepositRecord> listDepositRecords(String userAddress, int offset, int limit, boolean asc) {
		NavigableSet<Long> keys = asc ? this.items.navigableKeySet() : this.items.descendingKeySet();
		
		List<DepositRecord> records = keys.stream()
				.map(id -> this.items.get(id))
				.filter(record -> record.getUserAddress().equalsIgnoreCase(userAddress))
				.collect(Collectors.toList());
		
		return PagingResult.fromList(offset, limit, records);
	}
	
	@Override
	public BigDecimal getDepositSum(String currency) {
		return this.items.values().stream()
				.filter(r -> r.getCurrency().equalsIgnoreCase(currency))
				.map(r -> r.getAmount())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}
}

@Repository
class DepositDaoImpl extends BaseDaoImpl implements DepositDao {
	private static final RowMapper<DepositRecord> rowMapper = BeanPropertyRowMapper.newInstance(DepositRecord.class);

	@Override
	public void addDepositRecord(DepositRecord record) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {
			
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				String sql = "INSERT INTO t_deposit (user_address, currency, amount, tx_sender, tx_hash, create_time) VALUES (?, ?, ?, ?, ?, ?)";
				PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, record.getUserAddress());
				ps.setString(2, record.getCurrency());
				ps.setBigDecimal(3, record.getAmount());
				ps.setString(4, record.getTxSender());
				ps.setString(5, record.getTxHash());
				ps.setTimestamp(6, record.getCreateTime());
				return ps;
			}
		};
		
		KeyHolder keyHolder = new GeneratedKeyHolder();
		this.getJdbcTemplate().update(creator, keyHolder);
		record.setId(keyHolder.getKey().longValue());
	}

	@Override
	public PagingResult<DepositRecord> listDepositRecords(String userAddress, String currency, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_deposit WHERE user_address = ? AND currency = ? ORDER BY id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_deposit WHERE user_address = ? AND currency = ? ORDER BY id DESC LIMIT ?,?";
		}
		List<DepositRecord> records = this.getJdbcTemplate().query(listSql, rowMapper, userAddress, currency, offset, limit);
		
		String totalSql = "SELECT COUNT(id) FROM t_deposit WHERE user_address = ? AND currency = ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userAddress, currency);
		
		return new PagingResult<DepositRecord>(offset, limit, records, total);
	}
	
	@Override
	public PagingResult<DepositRecord> listDepositRecords(String userAddress, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_deposit WHERE user_address = ? ORDER BY id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_deposit WHERE user_address = ? ORDER BY id DESC LIMIT ?,?";
		}
		List<DepositRecord> records = this.getJdbcTemplate().query(listSql, rowMapper, userAddress, offset, limit);
		
		String totalSql = "SELECT COUNT(id) FROM t_deposit WHERE user_address = ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userAddress);
		
		return new PagingResult<DepositRecord>(offset, limit, records, total);
	}
	
	@Override
	public BigDecimal getDepositSum(String currency) {
		String sql = "SELECT SUM(amount) FROM t_deposit WHERE currency = ?";
		BigDecimal sum = this.getJdbcTemplate().queryForObject(sql, BigDecimal.class, currency);
		return sum == null ? BigDecimal.ZERO : sum;
	}
}
