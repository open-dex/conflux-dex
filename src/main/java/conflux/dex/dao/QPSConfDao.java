package conflux.dex.dao;

import conflux.dex.config.qps.LimitedResource;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;

public interface QPSConfDao {
    List<LimitedResource> listAll();
    int save(LimitedResource conf);
    int delete(long id);
}

@Repository
class QPSConfDaoImpl extends BaseDaoImpl implements QPSConfDao {
    @Override
    public List<LimitedResource> listAll() {
        String sql = "SELECT * FROM t_qps_limit order by id desc";
        return getJdbcTemplate().query(sql, (rs, rownum)->{
            LimitedResource bean = LimitedResource.buildResource(rs.getString("ip_url"));
            bean.id = rs.getLong("id");
            bean.rate = rs.getDouble("rate");
            return bean;
        });
    }

    @Override
    public int save(LimitedResource conf) {
        String sql = "insert into t_qps_limit(`ip_url`, `rate`) values (?, ?) ON DUPLICATE KEY UPDATE rate=?";
        int i = insertWithKeyHolder(sql, ps -> {
            try {
                ps.setString(1, conf.key);
                ps.setDouble(2, conf.rate);
                ps.setDouble(3, conf.rate);
            } catch (SQLException throwables) {
                throw new RuntimeException("PreparedStatement set value exception.", throwables);
            }
        }, id -> conf.id = id);
        return i;
    }

    @Override
    public int delete(long id) {
        String sql = "DELETE FROM t_qps_limit WHERE id = ?";
        return this.getJdbcTemplate().update(sql, id);
    }
}
