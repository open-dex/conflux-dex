package conflux.dex.service.blockchain.settle.prune;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import org.web3j.abi.datatypes.generated.Uint256;

import conflux.dex.blockchain.EncodeUtils;
import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.dao.DexDao;
import conflux.dex.model.DataPruneRecord;
import conflux.dex.model.SettlementStatus;
import conflux.dex.service.blockchain.settle.Settleable;
import conflux.web3j.RpcException;

public abstract class PruneSettlement extends Settleable {
	
	private String contract;
	private BigInteger gasLimit;
	private BigInteger storageLimit;

	protected PruneSettlement(String contract, BigInteger gasLimit, int batchSize) {
		super(0, null, 0);
		
		this.contract = contract;
		this.gasLimit = gasLimit;
		this.storageLimit = BlockchainConfig.instance.batchStorageLimit(batchSize);
	}
	
	protected abstract String encodeData() throws RpcException;
	
	@Override
	public Context getSettlementContext(DexDao dao) throws Exception {
		String data = this.encodeData();
		return new Context(this.contract, data, this.gasLimit, this.storageLimit);
	}
	
	public static PruneSettlement updateBoomflowTimestamp(BigInteger gasLimit, long timestamp) {
		return new UpdateTimestampSettlement(Domain.boomflow().verifyingContract, gasLimit, timestamp);
	}
	
	public static PruneSettlement updateCrclTimestamp(String crcl, BigInteger gasLimit, long timestamp) {
		return new UpdateTimestampSettlement(crcl, gasLimit, timestamp);
	}
	
	public static PruneSettlement deleteCrclRequests(String crcl, BigInteger gasLimit, List<DataPruneRecord> records, String configKey) {
		return new DeleteCrclRequestsSettlement(crcl, gasLimit, records, configKey);
	}

}

class UpdateTimestampSettlement extends PruneSettlement {
	
	private long timestamp;
	
	public UpdateTimestampSettlement(String contract, BigInteger gasLimit, long timestamp) {
		super(contract, gasLimit, 1);
		
		this.timestamp = timestamp;
	}

	@Override
	protected String encodeData() throws RpcException {
		return EncodeUtils.encode("setTimestamp", new Uint256(this.timestamp));
	}

	@Override
	protected void update(DexDao dao, SettlementStatus status, String txHash, long txNonce) {
		// do nothing
	}
	
}

abstract class DeleteObsoleteDataSettlement extends PruneSettlement {
	
	private List<DataPruneRecord> records;

	protected DeleteObsoleteDataSettlement(String contract, BigInteger gasLimit, List<DataPruneRecord> records) {
		super(contract, gasLimit, records.size());
		
		this.records = records;
	}

	@Override
	protected String encodeData() throws RpcException {
		List<String> hashes = this.records.stream()
				.map(r -> r.getHash())
				.collect(Collectors.toList());
		
		return EncodeUtils.encode("removeObsoleteData", EncodeUtils.hex2Bytes32Array(hashes));
	}
	
}

/**
 * At CRCL.sol,
 * function removeObsoleteData(bytes32[] memory hashes)
 */
class DeleteCrclRequestsSettlement extends DeleteObsoleteDataSettlement {
	
	private String configKey;
	private long maxId;

	public DeleteCrclRequestsSettlement(String crcl, BigInteger gasLimit, List<DataPruneRecord> records, String configKey) {
		super(crcl, gasLimit, records);
		
		this.configKey = configKey;
		this.maxId = records.get(records.size() - 1).getId();
	}

	@Override
	protected void update(DexDao dao, SettlementStatus status, String txHash, long txNonce) {
		if (status == SettlementStatus.OnChainConfirmed) {
			dao.setConfig(this.configKey, String.valueOf(this.maxId));
		}
	}
	
}
