package conflux.dex.dao;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

class BaseDaoImpl extends JdbcDaoSupport {
	
	@Autowired
	public void init(DataSource ds) {
		this.setDataSource(ds);
	}
	
	// Add more template method here, e.g. get, paging, update, insert ...

	public int insertWithKeyHolder(String sql, Consumer<PreparedStatement> fillArgs, Consumer<Long> idReceiver){
		KeyHolder keyHolder = new GeneratedKeyHolder();
		int cnt = this.getJdbcTemplate().update(con->{
			PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			fillArgs.accept(ps);
			return ps;
		}, keyHolder);
		if (cnt == 1) {
			Optional.ofNullable(keyHolder.getKey()).ifPresent(n->idReceiver.accept(n.longValue()));
		} else if (cnt > 1) {
			// 'replace into' will got two keys
			logger.warn("Generated more than one keys, please pay attention.");
			idReceiver.accept(new GeneratedKeyHolder(keyHolder.getKeyList().subList(0,1)).getKey().longValue());
		}
		return cnt;
	}


	public int deleteByIds(Collection<Long> ids, String table) {
		if (ids.isEmpty()) {
			return 0;
		}
		String inSql = String.join(",", Collections.nCopies(ids.size(), "?"));
		String sql = String.format("delete from %s where id in  (%s)", table, inSql);
		return this.getJdbcTemplate().update(sql, ids.toArray());
	}
}
