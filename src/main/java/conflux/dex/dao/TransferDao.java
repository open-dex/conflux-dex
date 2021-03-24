package conflux.dex.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.google.common.base.Enums;
import com.google.gson.Gson;

import conflux.dex.common.BusinessFault;
import conflux.dex.model.PagingResult;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.TransferRecord;

public interface TransferDao {
	
	boolean addTransferRecord(TransferRecord record);
	
	default void mustAddTransferRecord(TransferRecord record) {
		if (!this.addTransferRecord(record)) {
			throw BusinessFault.RecordAlreadyExists.rise();
		}
	}
	
	void updateTransferSettlement(long id, SettlementStatus status, String txHash, long txNonce);
	
	PagingResult<TransferRecord> listTransferRecords(String userAddress, String currency, int offset, int limit, boolean asc);
	PagingResult<TransferRecord> listTransferRecords(String userAddress, int offset, int limit, boolean asc);
	
	List<TransferRecord> listTransferRecordsByStatus(SettlementStatus status);
	List<TransferRecord> listTransferRecords(String currency, long idOffset, int limit);

}

@Repository
class TransferDaoImpl extends BaseDaoImpl implements TransferDao {
	
	private static final RowMapper<TransferRecord> ROW_MAPPER = new RowMapper<TransferRecord>() {

		@Override
		public TransferRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			TransferRecord record = new TransferRecord();
			
			record.setId(rs.getLong("id"));
			record.setUserAddress(rs.getString("user_address"));
			record.setCurrency(rs.getString("currency"));
			record.setRecipients(jsonToRecipients(rs.getString("recipients")));
			record.setTimestamp(rs.getLong("timestamp"));
			record.setHash(rs.getString("hash"));
			record.setSignature(rs.getString("signature"));
			record.setStatus(Enums.getIfPresent(SettlementStatus.class, rs.getString("status")).get());
			record.setTxHash(rs.getString("tx_hash"));
			record.setTxNonce(rs.getLong("tx_nonce"));
			record.setCreateTime(rs.getTimestamp("create_time"));
			record.setUpdateTime(rs.getTimestamp("update_time"));
			
			return record;
		}
		
	};
	
	private static final Gson GSON = new Gson();
	
	private static Map<String, BigDecimal> jsonToRecipients(String json) {
		Map<String, BigDecimal> result = new HashMap<String, BigDecimal>();
		
		Map<?, ?> recipients = GSON.fromJson(json, Map.class);
		for (Object key : recipients.keySet()) {
			result.put(key.toString(), new BigDecimal(recipients.get(key).toString()));
		}
		
		return result;
	}
	
	private static String recipientsToJson(Map<String, BigDecimal> recipients) {
		return GSON.toJson(recipients);
	}

	@Override
	public boolean addTransferRecord(TransferRecord record) {
		PreparedStatementCreator creator = new PreparedStatementCreator() {
			
			@Override
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				String sql = "INSERT IGNORE INTO t_transfer (user_address, currency, recipients, timestamp, hash, signature, status, tx_hash, tx_nonce, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
				PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, record.getUserAddress());
				ps.setString(2, record.getCurrency());
				ps.setString(3, recipientsToJson(record.getRecipients()));
				ps.setLong(4, record.getTimestamp());
				ps.setString(5, record.getHash());
				ps.setString(6, record.getSignature());
				ps.setString(7, record.getStatus().name());
				ps.setString(8, record.getTxHash());
				ps.setLong(9, record.getTxNonce());
				ps.setTimestamp(10, record.getCreateTime());
				ps.setTimestamp(11, record.getUpdateTime());
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
	public void updateTransferSettlement(long id, SettlementStatus status, String txHash, long txNonce) {
		if (StringUtils.isEmpty(txHash)) {
			String sql = "UPDATE t_transfer SET status = ?, update_time = UTC_TIMESTAMP() WHERE id = ?";
			this.getJdbcTemplate().update(sql, status.name(), id);
		} else {
			String sql = "UPDATE t_transfer SET status = ?, tx_hash = ?, tx_nonce = ?, update_time = UTC_TIMESTAMP() WHERE id = ?";
			this.getJdbcTemplate().update(sql, status.name(), txHash, txNonce, id);
		}
	}

	@Override
	public PagingResult<TransferRecord> listTransferRecords(String userAddress, String currency, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_transfer WHERE user_address = ? AND currency = ? ORDER BY id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_transfer WHERE user_address = ? AND currency = ? ORDER BY id DESC LIMIT ?,?";
		}
		List<TransferRecord> records = this.getJdbcTemplate().query(listSql, ROW_MAPPER, userAddress, currency, offset, limit);
		
		String totalSql = "SELECT COUNT(id) FROM t_transfer WHERE user_address = ? AND currency = ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userAddress, currency);
		
		return new PagingResult<TransferRecord>(offset, limit, records, total);
	}

	@Override
	public PagingResult<TransferRecord> listTransferRecords(String userAddress, int offset, int limit, boolean asc) {
		String listSql;
		if (asc) {
			listSql = "SELECT * FROM t_transfer WHERE user_address = ? ORDER BY id LIMIT ?,?";
		} else {
			listSql = "SELECT * FROM t_transfer WHERE user_address = ? ORDER BY id DESC LIMIT ?,?";
		}
		
		List<TransferRecord> records = this.getJdbcTemplate().query(listSql, ROW_MAPPER, userAddress, offset, limit);
		
		String totalSql = "SELECT COUNT(id) FROM t_transfer WHERE user_address = ?";
		int total = this.getJdbcTemplate().queryForObject(totalSql, Integer.class, userAddress);
		
		return new PagingResult<TransferRecord>(offset, limit, records, total);
	}

	@Override
	public List<TransferRecord> listTransferRecordsByStatus(SettlementStatus status) {
		// NOTE, do not query by status OnChainConfirmed.
		// Otherwise, the 'status' index may not take effect and cause full table scan.
		String sql = "SELECT * FROM t_transfer WHERE status = ? ORDER BY id";
		return this.getJdbcTemplate().query(sql, ROW_MAPPER, status.name());
	}
	
	@Override
	public List<TransferRecord> listTransferRecords(String currency, long idOffset, int limit) {
		String sql = "SELECT * FROM t_transfer WHERE currency = ? AND id >= ? ORDER BY id LIMIT ?";
		return this.getJdbcTemplate().query(sql, ROW_MAPPER, currency, idOffset, limit);
	}
	
}
