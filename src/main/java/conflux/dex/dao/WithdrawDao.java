package conflux.dex.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import conflux.dex.common.BusinessFault;
import conflux.dex.model.PagingResult;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.WithdrawRecord;
import conflux.dex.model.WithdrawType;

public interface WithdrawDao {
	
	boolean addWithdrawRecord(WithdrawRecord record);
	default void mustAddWithdrawRecord(WithdrawRecord record) {
		if (!this.addWithdrawRecord(record)) {
			throw BusinessFault.RecordAlreadyExists.rise();
		}
	}
	
	void updateWithdrawSettlement(long id, SettlementStatus status, String txHash, long txNonce);
	
	PagingResult<WithdrawRecord> listWithdrawRecords(String userAddress, String currency, int offset, int limit, boolean asc);
	PagingResult<WithdrawRecord> listWithdrawRecords(String userAddress, int offset, int limit, boolean asc);
	
	List<WithdrawRecord> listWithdrawRecordsByStatus(SettlementStatus status);
	List<WithdrawRecord> listWithdrawRecords(String currency, long idOffset, int limit);
	
	BigDecimal getWithdrawSum(String currency);
	BigDecimal getWithdrawSumByType(String currency, WithdrawType type);

}

@Repository
class WithdrawDaoImpl extends BaseDaoImpl implements WithdrawDao {
	private static final RowMapper<WithdrawRecord> rowMapper = BeanPropertyRowMapper.newInstance(WithdrawRecord.class);

	@Override
	public boolean addWithdrawRecord(WithdrawRecord record) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {
			
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				String sql = "INSERT IGNORE INTO t_withdraw (type, user_address, currency, amount, destination, burn, relay_contract, fee, timestamp, hash, signature, status, tx_hash, tx_nonce, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? ,? ,?, ?, ?)";
				PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, record.getType().name());
				ps.setString(2, record.getUserAddress());
				ps.setString(3, record.getCurrency());
				ps.setBigDecimal(4, record.getAmount());
				ps.setString(5, record.getDestination());
				ps.setBoolean(6, record.isBurn());
				ps.setString(7, record.getRelayContract());
				ps.setBigDecimal(8, record.getFee());
				ps.setLong(9, record.getTimestamp());
				ps.setString(10, record.getHash());
				ps.setString(11, record.getSignature());
				ps.setString(12, record.getStatus().name());
				ps.setString(13, record.getTxHash());
				ps.setLong(14, record.getTxNonce());
				ps.setTimestamp(15, record.getCreateTime());
				ps.setTimestamp(16, record.getUpdateTime());
				return ps;
			}
		};
		
		KeyHolder keyHolder = new GeneratedKeyHolder();
		if (this.getJdbcTemplate().update(creator, keyHolder) > 0) {
			record.setId(keyHolder.getKey().longValue());
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public void updateWithdrawSettlement(long id, SettlementStatus status, String txHash, long txNonce) {
		if (StringUtils.isEmpty(txHash)) {
			String sql = "UPDATE t_withdraw SET status = ?, update_time = UTC_TIMESTAMP() WHERE id = ?";
			this.getJdbcTemplate().update(sql, status.name(), id);
		} else {
			String sql = "UPDATE t_withdraw SET status = ?, tx_hash = ?, tx_nonce = ?, update_time = UTC_TIMESTAMP() WHERE id = ?";
			this.getJdbcTemplate().update(sql, status.name(), txHash, txNonce, id);
		}
	}

	@Override
	public PagingResult<WithdrawRecord> listWithdrawRecords(String userAddress, String currency, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_withdraw WHERE user_address = ? AND currency = ? ORDER BY id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_withdraw WHERE user_address = ? AND currency = ? ORDER BY id DESC LIMIT ?,?";
		}
		List<WithdrawRecord> records = this.getJdbcTemplate().query(listSql, rowMapper, userAddress, currency, offset, limit);
		
		String totalSql = "SELECT COUNT(id) FROM t_withdraw WHERE user_address = ? AND currency = ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userAddress, currency);
		
		return new PagingResult<WithdrawRecord>(offset, limit, records, total);
	}
	
	@Override
	public PagingResult<WithdrawRecord> listWithdrawRecords(String userAddress, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_withdraw WHERE user_address = ? ORDER BY id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_withdraw WHERE user_address = ? ORDER BY id DESC LIMIT ?,?";
		}
		
		List<WithdrawRecord> records = this.getJdbcTemplate().query(listSql, rowMapper, userAddress, offset, limit);
		
		String totalSql = "SELECT COUNT(id) FROM t_withdraw WHERE user_address = ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userAddress);
		
		return new PagingResult<WithdrawRecord>(offset, limit, records, total);
	}
	
	@Override
	public List<WithdrawRecord> listWithdrawRecordsByStatus(SettlementStatus status) {
		// NOTE, do not query by status OnChainConfirmed.
		// Otherwise, the 'status' index may not take effect and cause full table scan.
		String sql = "SELECT * FROM t_withdraw WHERE status = ? ORDER BY id";
		return this.getJdbcTemplate().query(sql, rowMapper, status.name());
	}
	
	@Override
	public List<WithdrawRecord> listWithdrawRecords(String currency, long idOffset, int limit) {
		String sql = "SELECT * FROM t_withdraw WHERE currency = ? AND id >= ? ORDER BY id LIMIT ?";
		return this.getJdbcTemplate().query(sql, rowMapper, currency, idOffset, limit);
	}
	
	@Override
	public BigDecimal getWithdrawSum(String currency) {
		String sql = "SELECT SUM(amount) FROM t_withdraw WHERE currency = ?";
		BigDecimal sum = this.getJdbcTemplate().queryForObject(sql, BigDecimal.class, currency);
		return sum == null ? BigDecimal.ZERO : sum;
	}
	
	@Override
	public BigDecimal getWithdrawSumByType(String currency, WithdrawType type) {
		String sql = "SELECT SUM(amount) FROM t_withdraw WHERE currency = ? AND type = ?";
		BigDecimal sum = this.getJdbcTemplate().queryForObject(sql, BigDecimal.class, currency, type.name());
		return sum == null ? BigDecimal.ZERO : sum;
	}
	
}
