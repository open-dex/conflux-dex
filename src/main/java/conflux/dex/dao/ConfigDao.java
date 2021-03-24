package conflux.dex.dao;

import com.codahale.metrics.annotation.Timed;
import conflux.dex.model.Config;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public interface ConfigDao {
	String KEY_SCAN_URL = "scan_url";
	String KEY_SCAN_PWD = "scan_pwd";

	String KEY_LAST_CLEAN_ORDER_ID = "last_clean_order_id";

	String KEY_LAST_CLEAN_TRAD_ID = "last_clean_trade_id";
	String KEY_LAST_CLEAN_TRADE_ID_MARKET_MAKER = "last_clean_trade_id_market_maker";
	String KEY_LAST_CLEAN_TRADE_ID_ROBOT = "last_clean_trade_id_robot";

	String KEY_CLEAN_BATCH_SIZE = "clean_batch_size";

	String KEY_ORDER_ARCHIVE_TABLE = "t_order_archive";
	String KEY_TRADE_ARCHIVE_TABLE = "t_trade_archive";

	String KEY_CLEAN_DAYS_GAP = "clean_days_gap";
	String KEY_CLEAN_DAYS_GAP_MARKET_MAKER = "clean_days_gap_market_maker";
	String KEY_CLEAN_DAYS_GAP_ROBOT = "clean_days_gap_robot";

	String KEY_LAST_TX_RESERVED_HASH = "last_tx_reserved_hash";
	String KEY_LAST_TX_RESERVED_NONCE = "last_tx_reserved_nonce";
	String ADMIN_NONCE_KEY = "ADMIN_NONCE_KEY";

	void setConfig(String name, String value);
	Optional<String> getConfig(String name);
	List<Config> listConfig(Collection<String> names);
	List<Config> listAll();

	default int getIntConfig(String key, int defaultV) {
		Optional<String> config = getConfig(key);
		if (config.isPresent()) {
			return Integer.parseInt(config.get());
		}
		return defaultV;
	}
}

class InMemoryConfigDao implements ConfigDao {
	private Map<String, Config> items = new ConcurrentHashMap<String, Config>();

	@Override
	public void setConfig(String name, String value) {
		this.items.put(name, new Config(name, value));
	}

	@Override
	public Optional<String> getConfig(String name) {
		return Optional.ofNullable(this.items.get(name)).map(Config::getName);
	}

	@Override
	public List<Config> listConfig(Collection<String> names) {
		return this.items.values().stream()
				.filter(c->names.contains(c.getName()))
				.collect(Collectors.toList());
	}

	@Override
	public List<Config> listAll() {
		return new ArrayList<>(this.items.values());
	}
}

@Repository
class ConfigDaoImpl extends BaseDaoImpl implements ConfigDao {

	private static final RowMapper<Config> configRowMapper = BeanPropertyRowMapper.newInstance(Config.class);

	@Override
	@Timed(name = "set")
	public void setConfig(String name, String value) {
		String sql = "REPLACE INTO t_config VALUES (?, ?)";
		this.getJdbcTemplate().update(sql, name, value);
	}

	@Override
	public Optional<String> getConfig(String name) {
		String sql = "SELECT value FROM t_config WHERE name = ?";
		List<String> values = this.getJdbcTemplate().queryForList(sql, String.class, name);
		return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
	}

	@Override
	public List<Config> listConfig(Collection<String> names) {
		if (names.isEmpty()) {
			return Collections.emptyList();
		}
		// reference : https://www.baeldung.com/spring-jdbctemplate-in-list
		String inSql = String.join(",", Collections.nCopies(names.size(), "?"));
		String sql = String.format("SELECT name, value FROM t_config WHERE name IN (%s)", inSql);
		List<Config> values = this.getJdbcTemplate().query(sql, names.toArray(), configRowMapper);
		return values;
	}

	@Override
	public List<Config> listAll() {
		String sql = String.format("SELECT name, value FROM t_config");
		List<Config> values = this.getJdbcTemplate().query(sql, configRowMapper);
		return values;
	}
}
