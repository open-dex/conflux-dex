package conflux.dex.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import conflux.dex.model.DailyLimit;

public interface DailyLimitDao {

	void addDailyLimit(DailyLimit dailyLimit);
	
	void removeDailyLimit(int productId);
	
	List<DailyLimit> listDailyLimitsByProductId(int id);

	List<DailyLimit> listAllDailyLimit();
}

class InMemoryDailyLimitDao extends IdAllocator implements DailyLimitDao {
	private Map<Integer, DailyLimit> items = new ConcurrentHashMap<Integer, DailyLimit>();

	private void addDailyLimitInMemory(DailyLimit dailyLimit) {
		this.items.put(dailyLimit.getId(), dailyLimit);
	}

	@Override
	public void addDailyLimit(DailyLimit dailyLimit) {
		dailyLimit.setId(this.getNextId());
		this.addDailyLimitInMemory(dailyLimit);
	}

	@Override
	public List<DailyLimit> listAllDailyLimit() {
		return new ArrayList<>(items.values());
	}

	@Override
	public void removeDailyLimit(int productId) {
		List<Integer> toRemove = new ArrayList<Integer>();
		for (Map.Entry<Integer, DailyLimit> entry : this.items.entrySet()) {
			DailyLimit dailyLimit = entry.getValue();
			if (dailyLimit.getProductId() == productId) {
				toRemove.add(entry.getKey());
			}
		}
		for (int key : toRemove)
			items.remove(key);
	}

	@Override
	public List<DailyLimit> listDailyLimitsByProductId(int id) {
		List<DailyLimit> result = new LinkedList<DailyLimit>();

		for (Map.Entry<Integer, DailyLimit> entry : this.items.entrySet()) {
			DailyLimit dailyLimit = entry.getValue();
			if (dailyLimit.getProductId() == id) {
				result.add(dailyLimit);
			}
		}

		return result;
	}
}

@Repository
@CacheConfig(cacheNames="dao.daily-limit")
class DailyLimitDaoImpl extends BaseDaoImpl implements DailyLimitDao {
	private static final RowMapper<DailyLimit> dailyLimitRowMapper = BeanPropertyRowMapper.newInstance(DailyLimit.class);

	private static final String SQL_INSERT = String.join(" ", "INSERT INTO t_dailylimit",
			"(product_id, start_time, end_time)",
			"VALUES (?, ?, ?)");

	@Override
	@Caching(evict = {
			@CacheEvict(key="'list'"),
			@CacheEvict(key="#dailyLimit.productId")
	})
	public void addDailyLimit(DailyLimit dailyLimit) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {

			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
				ps.setInt(1, dailyLimit.getProductId());
				ps.setTime(2, dailyLimit.getStartTime());
				ps.setTime(3, dailyLimit.getEndTime());
				return ps;
			}
		};

		KeyHolder keyHolder = new GeneratedKeyHolder();
		this.getJdbcTemplate().update(creator, keyHolder);
		dailyLimit.setId(keyHolder.getKey().intValue());
	}

	@Caching(evict = {
			@CacheEvict(key="'list'"),
			@CacheEvict(key="#productId")
	})
	@Override
	public void removeDailyLimit(int productId) {
		String sql = "DELETE FROM t_dailylimit WHERE product_id = ?";
		this.getJdbcTemplate().update(sql, productId);
	}

	@Cacheable
	@Override
	public List<DailyLimit> listDailyLimitsByProductId(int id) {
		String sql = "SELECT * FROM t_dailylimit WHERE product_id = ? ORDER BY start_time";
		return this.getJdbcTemplate().query(sql, dailyLimitRowMapper, id);
	}

	@Cacheable(key = "'list'")
	@Override
	public List<DailyLimit> listAllDailyLimit() {
		String sql = "SELECT * FROM t_dailylimit ORDER BY start_time";
		return this.getJdbcTemplate().query(sql, dailyLimitRowMapper);
	}
}
