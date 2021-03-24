package conflux.dex.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

import conflux.dex.common.BusinessFault;
import conflux.dex.event.Events;
import conflux.dex.model.PagingResult;
import conflux.dex.model.User;

public interface UserDao {
	
	boolean addUser(User user);

	List<User> getUsers(Collection<Long> ids);
	EntityGetResult<User> getUser(long id);
	EntityGetResult<User> getUserByName(String name);

	PagingResult<User> listUser(int offset, int limit);
	long getUserCount();
	
}

class InMemoryUserDao extends IdAllocator implements UserDao {
	private Map<Long, User> items = new ConcurrentHashMap<Long, User>();

	@Override
	public List<User> getUsers(Collection<Long> ids) {
		return ids.stream().map(id->this.items.get(id)).collect(Collectors.toList());
	}

	@Override
	public boolean addUser(User user) {
		if (this.getUserByName(user.getName()).get().isPresent()) {
			return false;
		}
		
		user.setId(this.getNextId());
		this.items.put(user.getId(), user);
		
		Events.NEW_USER_ADDED.fire(user);
		
		return true;
	}

	@Override
	public EntityGetResult<User> getUser(long id) {
		return EntityGetResult.ofNullable(this.items.get(id), BusinessFault.UserNotFound);
	}

	@Override
	public EntityGetResult<User> getUserByName(String name) {
		for (User user : this.items.values()) {
			if (user.getName().equalsIgnoreCase(name)) {
				return EntityGetResult.of(user);
			}
		}
		
		return EntityGetResult.notFound(BusinessFault.UserNotFound);
	}

	@Override
	public PagingResult<User> listUser(int offset, int size) {
		Collection<User> values = items.values();
		List<User> list = new ArrayList<>(values);
		list.sort((a, b) -> (int) (b.getId() - a.getId()));
		return new PagingResult<>(offset, size, list, values.size());
	}

	@Override
	public long getUserCount() {
		return this.items.size();
	}
}

@Repository
@CacheConfig(cacheNames = "dao.user")
class UserDaoImpl extends BaseDaoImpl implements UserDao {
	private static final RowMapper<User> userRowMapper = BeanPropertyRowMapper.newInstance(User.class);
	
	@Override
	@Caching(evict = {
			@CacheEvict(key = "#user.id", condition = "#user.id > 0"),
			@CacheEvict(key = "#user.name.toLowerCase()", condition = "#user.id > 0"),
	})
	public boolean addUser(User user) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {
			
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				String sql = "INSERT IGNORE INTO t_user (name) VALUES (?)";
				PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, user.getName());
				return ps;
			}
		};
		
		KeyHolder keyHolder = new GeneratedKeyHolder();
		if (this.getJdbcTemplate().update(creator, keyHolder) > 0) {
			user.setId(keyHolder.getKey().longValue());
			Events.NEW_USER_ADDED.fire(user);
			return true;
		} else {
			return false;
		}
	}

	@Override
	@Cacheable
	public EntityGetResult<User> getUser(long id) {
		String sql = "SELECT * FROM t_user WHERE id = ?";
		List<User> users = this.getJdbcTemplate().query(sql, userRowMapper, id);
		return EntityGetResult.of(users, BusinessFault.UserNotFound);
	}

	@Override
	public List<User> getUsers(Collection<Long> ids) {
		String sql = String.format("select * from t_user where id in (%s)",
				String.join(",", Collections.nCopies(ids.size(), "?")));
		return getJdbcTemplate().query(sql, ids.toArray(), userRowMapper);
	}

	@Override
	public PagingResult<User> listUser(int offset, int size) {
		String sql = "SELECT * FROM t_user ORDER BY id DESC LIMIT ?, ?";
		List<User> users = this.getJdbcTemplate().query(sql, userRowMapper, offset, size);
		long count = getUserCount();
		return new PagingResult<>(offset, size, users, (int)count);
	}

	@Override
	@Cacheable
	public EntityGetResult<User> getUserByName(String name) {
		String sql = "SELECT * FROM t_user WHERE name = ?";
		List<User> users = this.getJdbcTemplate().query(sql, userRowMapper, name);
		return EntityGetResult.of(users, BusinessFault.UserNotFound);
	}
	
	@Override
	public long getUserCount() {
		String sql = "SELECT COUNT(id) FROM t_user";
		return this.getJdbcTemplate().queryForObject(sql, Integer.class);
	}
}
