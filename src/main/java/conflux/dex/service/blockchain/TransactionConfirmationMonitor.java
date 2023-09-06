package conflux.dex.service.blockchain;

import java.math.BigInteger;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;

import conflux.dex.blockchain.crypto.legacy.NodejsWrapper;
import conflux.dex.common.BusinessException;
import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.QueueMetric;
import conflux.dex.common.Utils;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.config.ConfigService;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.SettlementStatus;
import conflux.dex.service.HealthService;
import conflux.dex.service.HealthService.PauseSource;
import conflux.dex.service.blockchain.settle.Settleable;
import conflux.dex.service.blockchain.settle.TransactionRecorder;
import conflux.web3j.Cfx;
import conflux.web3j.RpcException;
import conflux.web3j.contract.diagnostics.Recall;
import conflux.web3j.request.Epoch;
import conflux.web3j.response.Receipt;

@Service
public class TransactionConfirmationMonitor {
	private static final Logger logger = LoggerFactory.getLogger(TransactionConfirmationMonitor.class);
	
	private static final BigInteger CHECK_TX_DISCARDED_EPOCHS = BigInteger.valueOf(5);
	private static final int CHECK_TX_DISCARDED_INTERVAL_MILLIS = 1000;
	
	private static QueueMetric queue = Metrics.queue(TransactionConfirmationMonitor.class);
	private static Timer confirmPerf = Metrics.timer(TransactionConfirmationMonitor.class, "perf");
	private static Histogram epochsToExecuteStat = Metrics.histogram(TransactionConfirmationMonitor.class, "epochs", "execute");
	
	private BlockchainConfig config = new BlockchainConfig();
	
	private Cfx cfx;
	private DexDao dao;
	private HeartBeatService heartBeatService;
	private HealthService healthService;
	
	private String cfxUrl;
	
	// tx nonce -> item
	private NavigableMap<Long, Settleable> items = new ConcurrentSkipListMap<Long, Settleable>();
	
	@Autowired
	public TransactionConfirmationMonitor(Cfx cfx, DexDao dao, HeartBeatService heartBeatService) {
		this.cfx = cfx;
		this.dao = dao;
		this.heartBeatService = heartBeatService;
		
		Metrics.dump(this);
	}
	
	@Autowired
	public void setConfig(BlockchainConfig config) {
		this.config = config;
	}
	
	@Autowired
	public void setConfigService(ConfigService service) {
		this.cfxUrl = service.cfxUrl;
	}
	
	@Autowired
	public void setHealthService(HealthService healthService) {
		this.healthService = healthService;
	}
	
	public NavigableMap<Long, Settleable> getItems() {
		return items;
	}
	
	public void add(Settleable item) {
		if (item.getRecorder() == null) {
			return;
		}
		
		// In case of service restarted
		TransactionRecorder.Record record = item.getRecorder().getLast();
		if (record.epoch == null) {
			record.epoch = this.heartBeatService.getCurrentEpoch();
		}
		
		this.items.put(item.getSettledTxNonce(), item);
		
		queue.enqueue();
	}
	
	private boolean isSystemPaused() {
		return this.healthService != null && this.healthService.isPausedBy(PauseSource.Blockchain);
	}
	
	@Scheduled(initialDelay = 3_000, fixedDelay = 3_000)
	public void confirmTransactions() {
		if (this.isSystemPaused()) {
			return;
		}
		
		try {
			this.confirmTransactionsUnsafe();
		} catch (Exception e) {
			logger.error("failed to confirm transaction", e);
		}
	}

	public Settleable removeTx(Long txNonce) {
		Settleable removed = items.remove(txNonce);
		if (removed != null) {
			queue.dequeue();
		}
		return removed;
	}

	public CheckConfirmationResult checkConfirmation(Long txNonce) throws InterruptedException {
		Settleable settleable = items.get(txNonce);
		if (settleable == null) {
			throw BusinessException.validateFailed("tx not found in memory, it may be finished: " + txNonce);
		}
		BigInteger confirmedEpoch = this.cfx.getEpochNumber(Epoch.latestConfirmed()).sendAndGet();
		return checkConfirmation(settleable, confirmedEpoch);
	}
	
	public void confirmTransactionsUnsafe() throws RpcException, InterruptedException {
		BigInteger confirmedEpoch = this.cfx.getEpochNumber(Epoch.latestConfirmed()).sendAndGet();
		CheckConfirmationResult result = CheckConfirmationResult.Confirmed;
		
		while (!this.isSystemPaused() && !this.items.isEmpty() && result == CheckConfirmationResult.Confirmed) {
			Settleable settleable = this.items.firstEntry().getValue();

			result = this.checkConfirmation(settleable, confirmedEpoch);
			boolean removeMonitorItem = false;
			
			switch (result) {
			case Discarded:
				this.onTxDiscarded(settleable);
				break;
			case NotExecuted:
				removeMonitorItem = this.onTxNotExecuted(settleable, confirmedEpoch);
				break;
			case ReceiptValidationFailed:
				this.onTxValidationFailed(settleable);
				break;
			case ExecutionFailed:
				this.onTxFailed(settleable);
				break;
			case NotConfirmed:
				logger.debug("transaction not confirmed, hash = {}, confirmed epoch = {}", settleable.getSettledTxHash(), confirmedEpoch);
				break;
			case Confirmed:
				settleable.updateSettlement(this.dao, SettlementStatus.OnChainConfirmed);
				removeMonitorItem = true;
				break;
			default:
				throw BusinessException.internalError("unsupported CheckConfirmationResult: " + result);
			}
			
			if (removeMonitorItem) {
				this.items.pollFirstEntry();
				queue.dequeue();
			}
		}
	}
	
	private CheckConfirmationResult checkConfirmation(Settleable settleable, BigInteger confirmedEpoch) throws RpcException, InterruptedException {
		long start = System.currentTimeMillis();
		
		Optional<Receipt> receipt = settleable.getRecorder().getReceipt(this.cfx);
		
		// transaction unpacked yet
		if (!receipt.isPresent()) {
			if (this.isTxExists(settleable.getSettledTxHash())) {
				return CheckConfirmationResult.NotExecuted;
			} else {
				return CheckConfirmationResult.Discarded;
			}
		}
		
		// Multiple transactions sent, but not the last one packed.
		// In this case, need to update the txHash in database.
		String packedTxHash = receipt.get().getTransactionHash();
		if (!settleable.getSettledTxHash().equalsIgnoreCase(packedTxHash)) {
			settleable.updateTxHash(this.dao, packedTxHash);
		}

		if (receipt.get().getOutcomeStatus() != 0) {
			// dump data from contract for diagnostic
			logger.info("begin to dump failed tx {}", packedTxHash);
			try {
				NodejsWrapper.dump(this.cfxUrl, packedTxHash);
				logger.info("complete to dump failed epoch");
			} catch (Exception e) {
				logger.error("failed to dump failed epoch", e);
			}
			
			return CheckConfirmationResult.ExecutionFailed;
		}
		
		if (!settleable.matches(this.dao, receipt.get())) {
			logger.error("failed to validate receipt, settleable = {}, receipt = {}", settleable, Utils.toJson(receipt.get()));
			return CheckConfirmationResult.ReceiptValidationFailed;
		}
		
		Metrics.update(confirmPerf, start);
		epochsToExecuteStat.update(receipt.get().getEpochNumber().subtract(settleable.getSettledEpoch()).longValueExact());
		
		return receipt.get().getEpochNumber().compareTo(confirmedEpoch) <= 0
				? CheckConfirmationResult.Confirmed
				: CheckConfirmationResult.NotConfirmed;
	}
	
	/*
	 * When a transaction mined but not executed, full node RPC may return empty
	 * when get transaction by hash. So, just wait for at least 4 epochs and get
	 * transaction again.
	 */
	private boolean isTxExists(String txHash) throws InterruptedException {
		BigInteger targetEpoch = this.heartBeatService.getCurrentEpoch().add(CHECK_TX_DISCARDED_EPOCHS);
		
		while (true) {
			if (this.cfx.getTransactionByHash(txHash).sendAndGet().isPresent()) {
				return true;
			}
			
			BigInteger currentEpoch = this.heartBeatService.getCurrentEpoch();
			if (currentEpoch.compareTo(targetEpoch) > 0) {
				break;
			}
			
			Thread.sleep(CHECK_TX_DISCARDED_INTERVAL_MILLIS);
		}
		
		return false;
	}
	
	/*
	 * This is an extreme case that txpool on full node is full and some 
	 * transactions are discarded. In this case, DEX need to re-send these 
	 * kind of transactions with the original nonce and higher gas price.
	 */
	private void onTxDiscarded(Settleable settleable) {
		settleable.getRecorder().getLast().error = TransactionRecorder.Error.TxDiscarded;
		Events.TX_DISCARDED.fire(settleable);
		logger.warn("transaction discarded: txHash = {}, txNonce = {}, settleable = {}",
				settleable.getSettledTxHash(), settleable.getSettledTxNonce(), settleable);
	}
	
	/*
	 * There are several cases that transaction not packed in recent confirmed epoch:
	 * 
	 * 1) RPC server received transaction, but not propagate it to mining node timely.
	 * 2) RPC server received transaction, but not propagate it to mining node due to
	 * bug or temporary network issue.
	 * 3) Any unexpected issue in transaction pool of full node.
	 * 4) Gas price is too low in case of too many pending transactions.
	 * 
	 * Now, there is no way to judge the reason, so just wait for a longer time,
	 * e.g. 5 minutes or N epochs, and check the transaction again. If transaction still
	 * unpacked, then DEX need to re-send the transaction with original nonce and higher
	 * gas price.
	 */
	private boolean onTxNotExecuted(Settleable settleable, BigInteger confirmedEpoch) throws InterruptedException {
		String txHash = settleable.getSettledTxHash();
		
		// wait for transaction to be executed
		BigInteger elapsedEpochs = confirmedEpoch.subtract(settleable.getSettledEpoch());
		if (this.config.txMaxConfirmEpochs.compareTo(elapsedEpochs) > 0) {
			return false;
		}
		
		// try to re-send the unpacked transaction
		logger.warn("transaction unpacked in {} epochs, txHash = {}, txNonce = {}, settleable = {}",
				this.config.txMaxConfirmEpochs, txHash, settleable.getSettledTxNonce(), settleable);
		settleable.getRecorder().getLast().error = TransactionRecorder.Error.TxLongUnpacked;
		Events.TX_DISCARDED.fire(settleable);
		
		return true;
	}
	
	private void onTxValidationFailed(Settleable settleable) {
		// already added error log, here just pause system.
		this.healthService.pause(PauseSource.Blockchain, "failed to validate receipt for tx " + settleable.getSettledTxHash());
		settleable.updateSettlement(this.dao, SettlementStatus.OnChainReceiptValidationFailed);
	}
	
	private void onTxFailed(Settleable settleable) {
		Receipt receipt = this.cfx.getTransactionReceipt(settleable.getSettledTxHash()).sendAndGet().get();
		String txHash = settleable.getSettledTxHash();

		String errorData = receipt.getTxExecErrorMsg();
		
		settleable.updateSettlement(this.dao, SettlementStatus.OnChainFailed);
		
		if (!settleable.suppressError(errorData)) {
			String error = String.format("transaction execution failed: tx hash = %s, errorData = %s, settleable = %s", txHash, errorData, settleable);
			logger.error(error);
			this.healthService.pause(PauseSource.Blockchain, error);
		}
	}
	
	public enum CheckConfirmationResult {
		Discarded,
		NotExecuted,
		ExecutionFailed,
		ReceiptValidationFailed,
		NotConfirmed,
		Confirmed,
	}
}
