package conflux.dex.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import conflux.dex.model.DailyLimitRate;

public interface DailyLimitRateDao {

	void addDailyLimitRate(DailyLimitRate dailyLimit);
	
	Optional<DailyLimitRate> getDailyLimitRateByProductId(int id);

	List<DailyLimitRate> listDailyLimitRate();
}

class InMemoryDailyLimitRateDao extends IdAllocator implements DailyLimitRateDao {
	private Map<Integer, DailyLimitRate> items = new ConcurrentHashMap<Integer, DailyLimitRate>();

	private void addDailyLimitRateInMemory(DailyLimitRate dailyLimitRate) {
		this.items.put(dailyLimitRate.getProductId(), dailyLimitRate);
	}

	@Override
	public List<DailyLimitRate> listDailyLimitRate() {
		return new ArrayList<>(items.values());
	}

	@Override
	public void addDailyLimitRate(DailyLimitRate dailyLimitRate) {
		this.addDailyLimitRateInMemory(dailyLimitRate);
	}

	@Override
	public Optional<DailyLimitRate> getDailyLimitRateByProductId(int id) {
		return Optional.ofNullable(items.get(id));
	}

}

@Repository
@CacheConfig(cacheNames="dao.daily-limit-rate")
class DailyLimitRateDaoImpl extends BaseDaoImpl implements DailyLimitRateDao {
	private static final RowMapper<DailyLimitRate> dailyLimitRateRowMapper = BeanPropertyRowMapper.newInstance(DailyLimitRate.class);

	private static final String SQL_REPLACE = String.join(" ", "REPLACE INTO t_dailylimitrate",
			"(product_id, upper_limit_rate, lower_limit_rate, initial_price)",
			"VALUES (?, ?, ?, ?)");

	@Override
	@Caching(evict = {
			@CacheEvict(key="'list'"),
			@CacheEvict(key="#dailyLimitRate.productId")
	})
	public void addDailyLimitRate(DailyLimitRate dailyLimitRate) {
		this.getJdbcTemplate().update(SQL_REPLACE, 
				dailyLimitRate.getProductId(), 
				dailyLimitRate.getUpperLimitRate(),
				dailyLimitRate.getLowerLimitRate(),
				dailyLimitRate.getInitialPrice());
	}

	@Cacheable
	@Override
	public Optional<DailyLimitRate> getDailyLimitRateByProductId(int id) {
		String sql = "SELECT * FROM t_dailylimitrate WHERE product_id = ?";
		List<DailyLimitRate> result = this.getJdbcTemplate().query(sql, dailyLimitRateRowMapper, id);
		return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
	}

	@Cacheable(key="'list'")
	@Override
	public List<DailyLimitRate> listDailyLimitRate() {
		String sql = "SELECT * FROM t_dailylimitrate";
		List<DailyLimitRate> result = this.getJdbcTemplate().query(sql, dailyLimitRateRowMapper);
		return result;
	}
}
