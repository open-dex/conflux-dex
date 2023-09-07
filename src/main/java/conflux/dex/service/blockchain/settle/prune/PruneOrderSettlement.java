package conflux.dex.service.blockchain.settle.prune;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import conflux.dex.blockchain.TypedOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import conflux.dex.blockchain.EncodeUtils;
import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.dao.DexDao;
import conflux.dex.model.OrderPruneRecord;
import conflux.dex.model.SettlementStatus;
import conflux.dex.service.blockchain.settle.Settleable;
import conflux.web3j.RpcException;

public abstract class PruneOrderSettlement extends PruneSettlement {
	
	protected PruneOrderSettlement(BigInteger gasLimit, int batchSize) {
		super(Domain.boomflow().verifyingContract, gasLimit, batchSize);
	}
	
	public static Settleable upload(List<TypedOrder> orders, BigInteger gasLimit) {
		return new UploadPendingOrdersSettlement(orders, gasLimit);
	}
	
	public static Settleable delete(List<OrderPruneRecord> records, BigInteger gasLimit) {
		return new DeleteHistoricalOrdersSettlement(records, gasLimit);
	}

}

class UploadPendingOrdersSettlement extends PruneOrderSettlement {

	private List<TypedOrder> orders;

	public UploadPendingOrdersSettlement(List<TypedOrder> orders, BigInteger gasLimit) {
		super(gasLimit, orders.size());
		
		this.orders = orders;
	}
	
	@Override
	protected String encodeData() throws RpcException {
		return EncodeUtils.encode("recordOrders", EncodeUtils.typedDatas2Array(this.orders));
	}

	@Override
	protected void update(DexDao dao, SettlementStatus status, String txHash, long txNonce) {
		// do nothing
	}

}

class DeleteHistoricalOrdersSettlement extends PruneOrderSettlement {
	
	private List<OrderPruneRecord> records;
	
	public DeleteHistoricalOrdersSettlement(List<OrderPruneRecord> records, BigInteger gasLimit) {
		super(gasLimit, records.size());
		
		this.records = records;
	}
	
	@Override
	protected String encodeData() throws RpcException {
		List<String> orders = this.records.stream()
				.map(r -> r.getHash())
				.collect(Collectors.toList());
		
		return EncodeUtils.encode("removeObsoleteData", EncodeUtils.hex2Bytes32Array(orders));
	}
	
	@Override
	protected void update(DexDao dao, SettlementStatus status, String txHash, long txNonce) {
		if (status != SettlementStatus.OnChainConfirmed) {
			return;
		}
		
		dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				for (OrderPruneRecord record : records) {
					dao.deleteOrderPruneRecord(record.getTimestamp(), record.getOrderId());
				}
			}
			
		});
	}
	
}
