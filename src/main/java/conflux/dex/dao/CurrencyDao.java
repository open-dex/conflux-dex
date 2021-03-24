package conflux.dex.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

import com.codahale.metrics.annotation.Timed;

import conflux.dex.common.BusinessFault;
import conflux.dex.model.Currency;

public interface CurrencyDao {
	
	boolean addCurrency(Currency currency);
	boolean updateCurrency(Currency currency);
	
	List<Currency> listCurrencies();
	
	EntityGetResult<Currency> getCurrency(int id);
	EntityGetResult<Currency> getCurrencyByName(String name);
	EntityGetResult<Currency> getCurrencyByContractAddress(String address);

}

abstract class IdAllocator {
	private AtomicInteger idAllocator = new AtomicInteger();
	
	protected int getNextId() {
		return this.idAllocator.incrementAndGet();
	}
}

class InMemoryCurrencyDao extends IdAllocator implements CurrencyDao {
	private Map<Integer, Currency> items = new ConcurrentHashMap<Integer, Currency>();

	@Override
	public boolean addCurrency(Currency currency) {
		boolean exists = this.items.values().stream()
				.anyMatch(c -> c.getName().equalsIgnoreCase(currency.getName())
						|| c.getContractAddress().equalsIgnoreCase(currency.getContractAddress())
						|| c.getTokenAddress().equalsIgnoreCase(currency.getTokenAddress()));
		
		if (exists) {
			return false;
		}
		
		currency.setId(this.getNextId());
		this.items.put(currency.getId(), currency);
		
		return true;
	}
	
	@Override
	public boolean updateCurrency(Currency currency) {
		Currency existing = this.items.get(currency.getId());
		if (existing == null) {
			return false;
		}
		
		existing.setTokenAddress(currency.getTokenAddress());
		existing.setMinimumWithdrawAmount(currency.getMinimumWithdrawAmount());
		return true;
	}

	@Override
	public List<Currency> listCurrencies() {
		return new ArrayList<>(this.items.values());
	}

	@Override
	public EntityGetResult<Currency> getCurrency(int id) {
		return EntityGetResult.ofNullable(this.items.get(id), BusinessFault.CurrencyNotFound);
	}

	@Override
	public EntityGetResult<Currency> getCurrencyByName(String name) {
		Optional<Currency> matched = this.items.values().stream()
				.filter(c -> c.getName().equalsIgnoreCase(name))
				.findAny();
		
		return EntityGetResult.of(matched, BusinessFault.CurrencyNotFound);
	}
	
	@Override
	public EntityGetResult<Currency> getCurrencyByContractAddress(String address) {
		Optional<Currency> matched = this.items.values().stream()
				.filter(c -> c.getContractAddress().equalsIgnoreCase(address))
				.findAny();
		
		return EntityGetResult.of(matched, BusinessFault.CurrencyNotFound);
	}
}

@Repository
@CacheConfig(cacheNames = "dao.currency")
class CurrencyDaoImpl extends BaseDaoImpl implements CurrencyDao {
	private static final RowMapper<Currency> currencyRowMapper = BeanPropertyRowMapper.newInstance(Currency.class);

	@Override
	@Caching(evict = {
			@CacheEvict(key = "'list'", condition = "#currency.id > 0"),
			@CacheEvict(key = "#currency.id", condition = "#currency.id > 0"),
			@CacheEvict(key = "#currency.name.toLowerCase()", condition = "#currency.id > 0"),
			@CacheEvict(key = "#currency.contractAddress.toLowerCase()", condition = "#currency.id > 0"),
	})
	public boolean addCurrency(Currency currency) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {
			
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				String sql = "INSERT IGNORE INTO t_currency (name, contract_address, token_address, decimal_digits, cross_chain, minimum_withdraw_amount) VALUES (?, ?, ?, ?, ?, ?)";
				PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, currency.getName());
				ps.setString(2, currency.getContractAddress());
				ps.setString(3, currency.getTokenAddress());
				ps.setInt(4, currency.getDecimalDigits());
				ps.setBoolean(5, currency.isCrossChain());
				ps.setBigDecimal(6, currency.getMinimumWithdrawAmount());
				return ps;
			}
		};
		
		KeyHolder keyHolder = new GeneratedKeyHolder();
		if (this.getJdbcTemplate().update(creator, keyHolder) > 0) {
			currency.setId(keyHolder.getKey().intValue());
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	@Caching(evict = {
			@CacheEvict(key = "'list'", condition = "#result"),
			@CacheEvict(key = "#currency.id", condition = "#result"),
			@CacheEvict(key = "#currency.name.toLowerCase()", condition = "#result"),
			@CacheEvict(key = "#currency.contractAddress.toLowerCase()", condition = "#result"),
	})
	public boolean updateCurrency(Currency currency) {
		String sql = "UPDATE t_currency SET token_address = ?, minimum_withdraw_amount = ? WHERE id = ?";
		return this.getJdbcTemplate().update(sql, currency.getTokenAddress(), currency.getMinimumWithdrawAmount(), currency.getId()) > 0;
	}

	@Cacheable(key="'list'")
	@Override
	@Timed(name = "list")
	public List<Currency> listCurrencies() {
		String listSql = "SELECT * FROM t_currency ORDER BY name";
		List<Currency> currencies = this.getJdbcTemplate().query(listSql, currencyRowMapper);
		return currencies;
	}

	@Override
	@Cacheable
	public EntityGetResult<Currency> getCurrency(int id) {
		String sql = "SELECT * FROM t_currency WHERE id = ?";
		List<Currency> currencies = this.getJdbcTemplate().query(sql, currencyRowMapper, id);
		return EntityGetResult.of(currencies, BusinessFault.CurrencyNotFound).withParam(id);
	}

	@Override
	@Cacheable
	public EntityGetResult<Currency> getCurrencyByName(String name) {
		String sql = "SELECT * FROM t_currency WHERE name = ?";
		List<Currency> currencies = this.getJdbcTemplate().query(sql, currencyRowMapper, name);
		return EntityGetResult.of(currencies, BusinessFault.CurrencyNotFound).withParam(name);
	}
	
	@Override
	@Cacheable
	public EntityGetResult<Currency> getCurrencyByContractAddress(String address) {
		String sql = "SELECT * FROM t_currency WHERE contract_address = ?";
		List<Currency> currencies = this.getJdbcTemplate().query(sql, currencyRowMapper, address);
		return EntityGetResult.of(currencies, BusinessFault.CurrencyNotFound);
	}
	
}
