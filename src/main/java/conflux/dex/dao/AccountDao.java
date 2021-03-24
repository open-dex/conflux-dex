package conflux.dex.dao;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;

import conflux.dex.model.Account;
import conflux.dex.model.AccountStatus;

public interface AccountDao {
	
	boolean addAccount(Account account);

	boolean updateAccountBalance(long accountId, BigDecimal holdDelta, BigDecimal availableDelta);

	Optional<Account> getAccount(long userId, String currency);
	
	Optional<Account> getAccountById(long id);
	
	List<Account> listAccounts(long userId);
	
	void updateAccountStatus(long id, AccountStatus oldStatus, AccountStatus newStatus);

}

class InMemoryAccountDao extends IdAllocator implements AccountDao {
	private Map<Long, Account> items = new ConcurrentHashMap<Long, Account>();
	// map<userId, map<currency, account>>
	private Map<Long, Map<String, Account>> index = new ConcurrentHashMap<Long, Map<String,Account>>();

	@Override
	public boolean addAccount(Account account) {
		this.index.putIfAbsent(account.getUserId(), new ConcurrentHashMap<String, Account>());
		
		if (this.index.get(account.getUserId()).putIfAbsent(account.getCurrency(), account) != null) {
			return false;
		}
		
		account.setId(this.getNextId());
		this.items.put(account.getId(), account);
		
		return true;
	}

	@Override
	public boolean updateAccountBalance(long accountId, BigDecimal holdDelta, BigDecimal availableDelta) {
		Account account = this.items.get(accountId);
		if (account == null) {
			return false;
		}
		
		BigDecimal newHold = account.getHold().add(holdDelta);
		if (newHold.signum() < 0) {
			return false;
		}
		
		BigDecimal newAvailable = account.getAvailable().add(availableDelta);
		if (newAvailable.signum() < 0) {
			return false;
		}
		
		account.setHold(newHold);
		account.setAvailable(newAvailable);
		
		return true;
	}

	@Override
	public Optional<Account> getAccount(long userId, String currency) {
		Map<String, Account> currencyIndex = this.index.get(userId);
		return currencyIndex == null ? Optional.empty() : Optional.ofNullable(currencyIndex.get(currency));
	}
	
	@Override
	public Optional<Account> getAccountById(long id) {
		return Optional.ofNullable(this.items.get(id));
	}

	@Override
	public List<Account> listAccounts(long userId) {
		Map<String, Account> currencyIndex = this.index.get(userId);
		if (currencyIndex == null) {
			return Collections.emptyList();
		}
		
		return new ArrayList<>(currencyIndex.values());
	}
	
	@Override
	public void updateAccountStatus(long id, AccountStatus oldStatus, AccountStatus newStatus) {
		Account account = this.items.get(id);
		if (account != null && account.getStatus() == oldStatus) {
			account.setStatus(newStatus);
		}
	}
}

@Repository
//@CacheConfig(cacheNames = "dao.account")
class AccountDaoImpl extends BaseDaoImpl implements AccountDao {
	private static final RowMapper<Account> accountRowMapper = BeanPropertyRowMapper.newInstance(Account.class);

	/*
	@Caching(evict = {
			@CacheEvict(key="new org.springframework.cache.interceptor.SimpleKey(#account.userId, #account.currency)"),
			@CacheEvict(key="new org.springframework.cache.interceptor.SimpleKey(#account.userId)",
					cacheNames = "dao.account.list"),
			@CacheEvict(key = "#account.id"),
	})
	*/
	@Override
	public boolean addAccount(Account account) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {
			
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				String sql = "INSERT IGNORE INTO t_account (user_id, currency, hold, available, status) VALUES (?, ?, ?, ?, ?)";
				PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				ps.setLong(1, account.getUserId());
				ps.setString(2, account.getCurrency());
				ps.setBigDecimal(3, account.getHold());
				ps.setBigDecimal(4, account.getAvailable());
				ps.setString(5, account.getStatus().name());
				return ps;
			}
		};
		
		KeyHolder keyHolder = new GeneratedKeyHolder();
		
		if (this.getJdbcTemplate().update(creator, keyHolder) > 0) {
			account.setId(keyHolder.getKey().longValue());
			return true;
		} else {
			return false;
		}
	}

	@Metered
	@Override
    /*
	@Caching(evict = {
			@CacheEvict(keyGenerator = "KeyGeneratorId2UserIdAndCurrency"),
			@CacheEvict(keyGenerator = "KeyGeneratorId2UserId", cacheNames = "dao.account.list"),
			@CacheEvict(key = "#accountId"),
	})
     */
	public boolean updateAccountBalance(long accountId, BigDecimal holdDelta, BigDecimal availableDelta) {
		String sql = "UPDATE t_account SET hold = hold + ?, available = available + ? WHERE id = ? AND hold + ? >= 0 AND available + ? >= 0";
		return this.getJdbcTemplate().update(sql, holdDelta, availableDelta, accountId, holdDelta, availableDelta) > 0;
	}

	@Metered
//    @Cacheable
	@Override
	public Optional<Account> getAccount(long userId, String currency) {
		String sql = "SELECT * FROM t_account WHERE user_id = ? AND currency = ?";
		List<Account> accounts = this.getJdbcTemplate().query(sql, accountRowMapper, userId, currency);
		return accounts.isEmpty() ? Optional.empty() : Optional.of(accounts.get(0));
	}

	@Metered
//	@Cacheable
	@Override
	public Optional<Account> getAccountById(long id) {
		String sql = "SELECT * FROM t_account WHERE id = ?";
		List<Account> accounts = this.getJdbcTemplate().query(sql, accountRowMapper, id);
		return accounts.isEmpty() ? Optional.empty() : Optional.of(accounts.get(0));
	}

//	@Cacheable(cacheNames = "dao.account.list")
	@Override
	@Timed(name = "list")
	public List<Account> listAccounts(long userId) {
		String listSql = "SELECT * FROM t_account WHERE user_id = ? ORDER BY currency";
		List<Account> accounts = this.getJdbcTemplate().query(listSql, accountRowMapper, userId);
		
		return accounts;
	}

	/*
	 * When update, evict cache key in three way.
	 * @param id
	 * @param oldStatus
	 * @param newStatus
	 */
	/*
	@Caching(evict = {
	        @CacheEvict(keyGenerator = "KeyGeneratorId2UserIdAndCurrency"),
	        @CacheEvict(keyGenerator = "KeyGeneratorId2UserId", cacheNames = "dao.account.list"),
	        @CacheEvict(key = "#id"),
    })
	 */
	@Override
	public void updateAccountStatus(long id, AccountStatus oldStatus, AccountStatus newStatus) {
		String sql = "UPDATE t_account SET status = ? WHERE id = ? AND status = ?";
		this.getJdbcTemplate().update(sql, newStatus.name(), id, oldStatus.name());
	}
	
}

/**
 * Main uses: mapping account id to user id and currency , which are used as cache key.
 * The method marked with evict annotation with this key generator, should have account id parameter.
 */
//@Component("KeyGeneratorId2UserIdAndCurrency")
class KeyGeneratorId2UserIdAndCurrency extends SimpleKeyGenerator {
    @Override
    public Object generate(Object target, Method method, Object... params) {
        AccountDao dao = (AccountDao) target;
        // Access account id
        Long id = (Long)params[0];
        Optional<Account> accountById = dao.getAccountById(id);
        if (accountById.isPresent()) {
            Account account = accountById.get();
            return super.generate(target, method, account.getUserId(), account.getCurrency());
        }
        // If the specified account does't exist, then the key pair(UserId,currency) is useless,
		// free to set with any value.
        return super.generate(target, method, id, "NonUserId", "NonCurrencyId");
    }
}

/**
 * Used as cache key for list().
 */
//@Component("KeyGeneratorId2UserId")
class KeyGeneratorId2UserId extends SimpleKeyGenerator {
	@Override
	public Object generate(Object target, Method method, Object... params) {
		AccountDao dao = (AccountDao) target;
		// Access account id
		Long id = (Long)params[0];
		Optional<Account> accountById = dao.getAccountById(id);
		if (accountById.isPresent()) {
			Account account = accountById.get();
			return super.generate(target, method, account.getUserId());
		}
		// If the specified account does't exist, then the key pair(UserId,currency) is useless,
		// free to set with any value.
		return super.generate(target, method, id, "NonUserId");
	}
}