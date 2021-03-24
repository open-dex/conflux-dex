package conflux.dex.dao;

import conflux.dex.common.BusinessException;
import conflux.dex.model.BinLog;
import conflux.dex.model.DepositRecord;
import conflux.dex.model.Order;
import conflux.dex.model.PagingResult;
import conflux.dex.model.Trade;
import conflux.dex.model.UserTradeMap;
import conflux.dex.model.WithdrawRecord;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class MiscDAO extends BaseDaoImpl{
    public static final RowMapper<Trade> tradeRowMapper = BeanPropertyRowMapper.newInstance(Trade.class);
    private static final RowMapper<BinLog> binLogRowMapper = (rs, rowNum) -> {
        BinLog log = new BinLog();
        log.setLogName(rs.getString("Log_name"));
        log.setFileSize(rs.getLong("File_size"));
        log.setEncrypted(rs.getString("Encrypted"));
        return log;
    };

    public List<Map<String, Object>> countOrderByStatus() {
        String sql = "select status, count(*) as count from t_order group by status";
        return getJdbcTemplate().query(sql, new ColumnMapRowMapper());
    }
    public List<Map<String, Object>> countOrderCancelByStatus() {
        String sql = "select status, count(*) as count from t_order_cancel group by status";
        return getJdbcTemplate().query(sql, new ColumnMapRowMapper());
    }

    public List<Map<String, Object>> countTransferByStatus() {
        String sql = "select status, count(*) as count from t_transfer group by status";
        return getJdbcTemplate().query(sql, new ColumnMapRowMapper());
    }

    public List<Map<String, Object>> countWithdrawByStatus() {
        String sql = "select status, count(*) as count from t_withdraw group by status";
        return getJdbcTemplate().query(sql, new ColumnMapRowMapper());
    }

    public List<Map<String, Object>> countOrderPruneByStatus() {
        String sql = "select 'ALL' status, count(*) as count from t_order_prune group by status";
        return getJdbcTemplate().query(sql, new ColumnMapRowMapper());
    }

    public List<Map<String, Object>> countTradeByStatus() {
        String sql = "select status, count(*) as count from t_trade group by status";
        return getJdbcTemplate().query(sql, new ColumnMapRowMapper());
    }

    public List<Trade> listTradeByTxNonce(String nonce) {
        // tx nonce is an index, tx hash is not.
        String sql = "select * from t_trade where tx_nonce = ?";
        return getJdbcTemplate().query(sql, tradeRowMapper, nonce);
    }

    public PagingResult<?> listOrders(long userId, Integer productId, int skip, int size) {
        String sql = "select t.* " +
                "from t_order t " +
                "where user_id = ? and t.product_id= ?";
        return paging(skip, size, sql, new ColumnMapRowMapper(), userId, productId);
    }

    public PagingResult<?> listTrades(long userId, Integer productId, int skip, int size) {
        String sql = "select t.* " +
                "from t_trade_user_map m join t_trade t on m.trade_id=t.id " +
                "where user_id = ? and m.product_id= ?";
        return paging(skip, size, sql, new ColumnMapRowMapper(), userId, productId);
    }

    RowMapper<WithdrawRecord> withdrawRecordRowMapper = BeanPropertyRowMapper.newInstance(WithdrawRecord .class);
    public PagingResult<WithdrawRecord> listWithdraw(String currency, int skip, int size) {
        String sql = "SELECT * FROM t_withdraw where currency = ? or '' = ? order by id desc";
        return paging(skip, size, sql, withdrawRecordRowMapper, currency, currency);
    }

    RowMapper<DepositRecord> depositRecordRowMapper = BeanPropertyRowMapper.newInstance(DepositRecord.class);
    public PagingResult<DepositRecord> listDeposit(String currency, int skip, int size) {
        String sql = "SELECT * FROM t_deposit where currency = ? or '' = ? order  by id desc";
        return paging(skip, size, sql, depositRecordRowMapper, currency, currency);
    }

    @NotNull
    public <T> PagingResult<T> paging(int skip, int size, String sql,
                                                    RowMapper<T> rowMapper,
                                                    Object ... args) {
        int count = count(sql, args);
        List<T> list = Collections.emptyList();
        if (count > 0) {
            sql += " limit ?, ?";
            Object[] argsWithPage = new Object[args.length+2];
            System.arraycopy(args, 0, argsWithPage, 0, args.length);
            argsWithPage[args.length] = skip;
            argsWithPage[args.length + 1] = size;
            list = getJdbcTemplate().query(sql, rowMapper, argsWithPage);
        }
        return new PagingResult<T>(skip, size, list, count);
    }

    public int count(String sql, Object ... args) {
        if (sql.contains("from")) {
            sql = "select count(*) " + sql.substring(sql.indexOf("from"));
        } else {
            sql = "select count(*) " + sql.substring(sql.indexOf("FROM"));
        }
        return getJdbcTemplate().queryForObject(sql, Integer.class, args);
    }

    public List<BinLog> showBinaryLogs() {
        // show variables like 'log_bin';
        String sql = "show binary logs;";
        try {
            return getJdbcTemplate().query(sql, binLogRowMapper);
        } catch (UncategorizedSQLException e) {
            // prevent java.sql.SQLException: You are not using binary logging
            BinLog log = new BinLog();
            log.setLogName(e.getSQLException().getMessage());
            log.setFileSize(0L);
            return Collections.singletonList(log);
        }
    }

    public List<Object> purge(String to) {
        String sql = "purge master logs to ?";
        try {
            return Collections.singletonList(getJdbcTemplate().update(sql, to));
        } catch (UncategorizedSQLException e) {
            // Target log not found in binlog index
            throw BusinessException.system(e.getSQLException().getMessage());
        }
    }
}
