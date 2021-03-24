package conflux.dex.service.blockchain.settle;

import java.math.BigInteger;

import conflux.dex.controller.AddressTool;
import conflux.dex.model.FeeData;
import conflux.web3j.types.Address;
import org.slf4j.Logger;
import org.springframework.util.StringUtils;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.common.Utils;
import conflux.dex.common.worker.BatchWorker.Batchable;
import conflux.dex.dao.DexDao;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.Trade;
import conflux.dex.model.TransferRecord;
import conflux.dex.model.WithdrawRecord;
import conflux.web3j.response.Receipt;
import conflux.web3j.types.RawTransaction;

public abstract class Settleable implements Batchable {
	
	public static class Context {
		
		public static Context IGNORE;
		
		static {
			IGNORE = new Context(null, null, BigInteger.ZERO, BigInteger.ZERO);
			IGNORE.ignored = true;
		}
		
		// Indicates if the on-chain settlement could be ignored.
		// E.g. cancel a maker order that never filled.
		public boolean ignored;
		public Address contract;
		public String data;
		public BigInteger gasLimit;
		public BigInteger storageLimit;
		
		public Context(String contract, String data, BigInteger gasLimit, BigInteger storageLimit) {
			this.contract = AddressTool.address(contract);
			this.data = data;
			this.gasLimit = gasLimit;
			this.storageLimit = storageLimit;
		}
		
		public static Context boomflow(String data, BigInteger gasLimit, BigInteger storageLimit) {
			return new Context(Domain.boomflow().verifyingContract, data, gasLimit, storageLimit);
		}
		
	}
	
	private long id;
	private SettlementStatus status = SettlementStatus.OffChainSettled;
	private TransactionRecorder recorder;
	
	protected Settleable(long id, String settledTxHash, long settledTxNonce) {
		this.id = id;
		
		// re-settle item when service restarted
		if (!StringUtils.isEmpty(settledTxHash) && settledTxNonce > 0) {
			this.recorder = new TransactionRecorder(settledTxHash, settledTxNonce);
		}
	}
	
	public String getTypeName() {
		return this.getClass().getSimpleName();
	}
	
	public long getId() {
		return id;
	}
	
	public SettlementStatus getStatus() {
		return status;
	}
	
	public TransactionRecorder getRecorder() {
		return recorder;
	}
	
	public String getSettledTxHash() {
		return this.recorder == null ? null : this.recorder.getLast().txHash;
	}
	
	public long getSettledTxNonce() {
		return this.recorder == null ? 0 : this.recorder.getNonce();
	}
	
	public BigInteger getSettledEpoch() {
		return this.recorder == null ? null : this.recorder.getLast().epoch;
	}

	public abstract Context getSettlementContext(DexDao dao) throws Exception;

	public void updateSettlement(DexDao dao, SettlementStatus status) {
		this.update(dao, status, null, 0);
		this.status = status;
	}
	
	public void updateTxHash(DexDao dao, String txHash) {
		this.update(dao, this.status, txHash, this.recorder.getNonce());
	}
	
	public void updateSettlement(DexDao dao, SettlementStatus status, String txHash, RawTransaction tx) {
		this.update(dao, status, txHash, tx.getNonce().longValueExact());
		this.status = status;
		
		if (this.recorder == null) {
			this.recorder = new TransactionRecorder(txHash, tx);
		} else {
			this.recorder.addRecord(txHash, tx);
		}
	}
	
	protected abstract void update(DexDao dao, SettlementStatus status, String txHash, long txNonce);
	
	public boolean matches(DexDao dao, Receipt receipt) {
		// check nothing by default
		return true;
	}
	
	protected boolean matchesEventLogLength(Receipt receipt, int expectedLength, Logger logger) {
		if (receipt.getLogs() == null) {
			logger.error("failed to validate receipt: logs is null");
			return false;
		}
		
		int numLogs = receipt.getLogs().size();
		if (numLogs != expectedLength) {
			logger.error("failed to validate the length of receipt logs, expected = {}, actual = {}", expectedLength, numLogs);
			return false;
		}
		
		return true;
	}
	
	public boolean suppressError(String errorData) {
		return false;
	}
	
	@Override
	public boolean isBatchable() {
		return false;
	}

	@Override
	public boolean batchWith(Batchable other) {
		return false;
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public String toShortDisplay() {
		if (this.recorder == null) {
			return String.format("%s {id=%s, status=%s, txHash=null, txNonce=0}",
					this.getTypeName(), this.id, this.status);
		} else {
			return String.format("%s {id=%s, status=%s, txHash=%s, txNonce=%s}",
					this.getTypeName(), this.id, this.status,
					this.recorder.getLast().txHash,
					this.recorder.getNonce());
		}
	}

	@Override
	public String toLongDisplay() {
		return this.toString();
	}
	
	@Override
	public String toString() {
		return String.format("%s %s", this.getClass().getSimpleName(), Utils.toJson(this));
	}
	
	public static Settleable orderMatched(Trade trade, FeeData feeData) {
		return new TradeSettlement(trade, feeData);
	}
	
	public static Settleable orderCancelled(long orderId, String txHash, long txNonce) {
		return new CancelOrderSettlement(orderId, txHash, txNonce);
	}
	
	public static Settleable withdraw(WithdrawRecord record) {
		return new WithdrawSettlement(record);
	}
	
	public static Settleable instantExchangeOrderMatched(Trade trade) {
		return new InstantExchangeTradeSettlement(trade);
	}
	
	public static Settleable transfer(TransferRecord record) {
		return new TransferSettlement(record);
	}

}
