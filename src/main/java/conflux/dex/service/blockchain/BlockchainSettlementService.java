package conflux.dex.service.blockchain;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;

import conflux.dex.service.NonceKeeper;
import conflux.dex.tool.CfxTxSender;
import conflux.web3j.AMNAccount;
import conflux.web3j.RpcException;
import conflux.web3j.response.Receipt;
import conflux.web3j.response.UsedGasAndCollateral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;

import conflux.dex.blockchain.OrderBlockchain;
import conflux.dex.common.BusinessException;
import conflux.dex.common.BusinessFault;
import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.QueueMetric;
import conflux.dex.common.worker.BatchWorker;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.CancelOrderRequest;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.Trade;
import conflux.dex.model.TransferRecord;
import conflux.dex.model.WithdrawRecord;
import conflux.dex.service.FeeService;
import conflux.dex.service.HealthService;
import conflux.dex.service.HealthService.PauseSource;
import conflux.dex.service.blockchain.settle.Settleable;
import conflux.dex.service.blockchain.settle.TransactionRecorder;
import conflux.web3j.types.RawTransaction;
import conflux.web3j.types.SendTransactionResult;

@Service
public class BlockchainSettlementService extends BatchWorker<Settleable> {
	public static final String KEY_TX_WAIT_EXEC = "tx.wait.exec";
	private static final Logger logger = LoggerFactory.getLogger(BlockchainSettlementService.class);
	
	private static final QueueMetric queue = Metrics.queue(BlockchainSettlementService.class);
	private static final Timer perf = Metrics.timer(BlockchainSettlementService.class, "perf");
	private static final Timer perfSendTx = Metrics.timer(BlockchainSettlementService.class, "sign-send-tx");
	
	private DexDao dao;
	private OrderBlockchain blockchain;
	private TransactionConfirmationMonitor monitor;
	private HeartBeatService heartBeatService;
	private HealthService healthService;
	private TransactionRelayer txRelayer;
	
	private BlockchainConfig config;
	private FeeService feeService;
	private EvmTxService evmTxService;

	@Autowired
	public BlockchainSettlementService(ExecutorService executor, DexDao dao,
			OrderBlockchain blockchain,
			FeeService feeService,
			TransactionConfirmationMonitor monitor,
			BlockchainConfig config) {
		super(executor, "BlockchainSettlementService", config.settlementBatchSize, config.settlementBatchTimeoutMillis);
		this.feeService = feeService;
		this.setQueueMetric(queue);
		this.setHandleDataPerf(perf);
		
		this.dao = dao;
		this.blockchain = blockchain;
		this.monitor = monitor;
		this.config = config;
		
		Metrics.dump(this);
		
		Events.TX_DISCARDED.addHandler(data -> submit(data, true));		
		Events.ORDER_MATCHED.addHandler(data -> submit(Settleable.orderMatched(data.getTrade(), feeService.getData())));
		Events.ORDER_CANCELLED.addHandler(order -> {
			if (order.isEverMatched()) {
				submit(Settleable.orderCancelled(order.getId(), null, 0));
			}
		});
		Events.INSTANT_EXCHANGE_ORDER_MATCHED.addHandler(data -> submit(Settleable.instantExchangeOrderMatched(data.getTrade())));
		Events.WITHDRAW_SUBMITTED.addHandler(data -> submit(Settleable.withdraw(data.record)));
		Events.TRANSFER.addHandler(data -> submit(Settleable.transfer(data.record)));
	}

	public Settleable[] getTasks() {
		return getTasks(new Settleable[0]);
	}

	@Autowired
	public void setHeartBeatService(HeartBeatService heartBeatService) {
		this.heartBeatService = heartBeatService;
	}
	
	@Autowired
	public void setHealthService(HealthService healthService) {
		this.healthService = healthService;
	}
	
	@Autowired
	public void setTxRelayer(TransactionRelayer txRelayer) {
		this.txRelayer = txRelayer;
	}

	@Autowired
	public void setEvmTxService(EvmTxService svc) {
		this.evmTxService = svc;
	}
	@Override
	public void submit(Settleable data) {
		if (this.config.enabled) {
			super.submit(data);
		}
	}

	@Override
	protected void doWork(Settleable data) throws Exception {
		if (this.healthService != null && this.healthService.isPausedBy(PauseSource.Blockchain)) {
			Thread.sleep(this.config.settlementPausedSleepMillis);
			
			// throw to settle again
			throw BusinessFault.SystemPaused.rise();
		}
		
		this.validateNonceOnChain();
		
		this.settle(data);
	}
	
	private void validateNonceOnChain() throws Exception {
		AMNAccount admin = this.blockchain.getAdmin();
		
		// Check once for N settlements
		BigInteger offChainNonce = admin.getNonce();
		if (offChainNonce.divideAndRemainder(this.config.settlementNonceCheckInterval)[1].compareTo(BigInteger.ZERO) != 0) {
			return;
		}
		
		// not reached the too future threshold
		BigInteger onChainNonce = admin.getCfx().getNonce(admin.getAddress()).sendAndGet();
		if (onChainNonce.add(this.config.settlementNonceFutureMax).compareTo(offChainNonce) >= 0) {
			return;
		}
		
		// sleep for a while and settle again if queue is not full
		if (this.getPendingCount() <= this.config.settlementQueueCapacity) {
			logger.info("too many txs unpacked on full node and sleep for a while, onChainNonce = {}, offChainNonce = {}", onChainNonce, offChainNonce);
			Thread.sleep(this.config.settlementPausedSleepMillis);
			throw BusinessFault.SystemPaused.rise();
		}
		
		// Pause system if queue capacity reached.
		// This is happened when off chain TPS > on chain TPS
		String error = String.format("failed to validate nonce on chain, queue capacity reached, onChainNonce = %s, offChainNonce = %s, maxTooFuture = %s, queueCapacity = %s",
				onChainNonce, offChainNonce, this.config.settlementNonceFutureMax, this.config.settlementQueueCapacity);
		logger.error(error);
		this.healthService.pause(PauseSource.Blockchain, error);
		throw BusinessFault.SystemPaused.rise();
	}
	
	private void settle(Settleable data) throws Exception {
		Settleable.Context context;
		
		try {
			context = data.getSettlementContext(this.dao);
		} catch (Exception e) {
			String error = String.format("failed to encode data: error = %s, settleable = %s", e.getMessage(), data);
			logger.error(error, e);
			this.healthService.pause(PauseSource.Blockchain, error);
			
			// throw to settle again
			throw BusinessFault.SystemPaused.rise();
		}
		if (context == null) {
			logger.warn("settlement with null context, id {} nonce {} hash {}"
					, data.getId(), data.getSettledTxNonce(), data.getSettledTxHash());
			return;
		}
		
		// E.g. a cancelled maker order never matched, just mark it as OnChainConfirmed.
		if (context.ignored) {
			data.updateSettlement(this.dao, SettlementStatus.OnChainConfirmed);
			return;
		}
		
		TransactionRecorder recorder = data.getRecorder();
		String txHash = null;
		if (recorder == null) {
			// settle for the first time
			this.sendTransaction(data, context);
			txHash = data.getSettledTxHash();
		} else if (recorder.getLast().error == null && this.isSettledOnChain(recorder.getLast().txHash)) {
			// Service restarted and last item already settled on chain,
			// just mark it as OnChainSettled
			this.validateNonce(data, true);
		} else {
			// 1. re-send transaction on error
			// 2. service restarted and last item not settled on chain yet
			this.validateNonce(data, false);
			this.sendTransaction(data, context);
			txHash = data.getSettledTxHash();
		}
		
		data.updateSettlement(this.dao, SettlementStatus.OnChainSettled);
		
		this.monitor.add(data);

		if (txHash != null) {
			waitTxExecuted(data);
		}
	}

	private void waitTxExecuted(Settleable data) {
		String txHash = data.getSettledTxHash();
		while (true) {
			// maybe it has been paused by tx monitor already.
			if (this.healthService.getPauseSource().isPresent()) {
				break;
			}
			if (TransactionRecorder.Error.TxDiscarded.equals(data.getRecorder().getLast().error)) {
				// tx monitor may set the error to `TxDiscarded`, and fire an event in order to resubmit this tx.
				break;
			}
			Optional<Receipt> opt;
			try {
				opt = this.blockchain.getAdmin().getCfx().getTransactionReceipt(txHash).sendAndGet();
			} catch (RpcException e) {
				logger.error("rpc exception while fetching tx receipt, tx {}", txHash, e);
				opt = Optional.empty();
			}
			// not executed, or failed
			if (!opt.isPresent() || opt.get().getOutcomeStatus() != 0) {
				try {
					//noinspection BusyWait
					Thread.sleep(1_000);
				} catch (InterruptedException e) {
					break;
				}
				continue;
			}
			// transaction monitor will pause system if tx failed, do nothing here.
			break;
		}
	}

	private boolean isSettledOnChain(String txHash) {
		return this.blockchain.getAdmin().getCfx().getTransactionByHash(txHash).sendAndGet().isPresent();
	}
	
	private void fatal(Settleable data, String format, Object... args) {
		String error = String.format("%s. Settleable = %s", String.format(format, args), data);
		logger.error(error);
		System.err.println(error);
		System.exit(1);
	}
	
	private void validateNonce(Settleable data, boolean settledOnChain) {
		// do not validate nonce for transaction discarded or long unpacked cases.
		// just re-send tx with original nonce.
		if (data.getRecorder().getLast().error != null) {
			return;
		}
		
		long expectedNonce = data.getSettledTxNonce();
		if (settledOnChain) {
			expectedNonce++;
		}
		
		BigInteger adminNonce = this.blockchain.getAdmin().getNonce();
		if (BigInteger.valueOf(expectedNonce).compareTo(adminNonce) != 0) {
			logger.info("nonce mismatch: settledOnChain = {}, settledNonce = {}, adminNonce = {}, Settleable = {}",
					settledOnChain, data.getSettledTxNonce(), adminNonce, data);
		}
	}
	
	private void sendTransaction(Settleable data, Settleable.Context context) throws Exception {
		AMNAccount admin = this.blockchain.getAdmin();
		TransactionRecorder txRecorder = data.getRecorder();
		boolean resendOnError = txRecorder != null && txRecorder.getLast().error != null;
		
		// For discarded case, use the original tx nonce.
		// Otherwise, use the latest tx nonce of DEX admin.
		BigInteger nonce = resendOnError
				? BigInteger.valueOf(data.getSettledTxNonce())
				: admin.getNonce();

		BigInteger epoch = this.heartBeatService == null
				? admin.getCfx().getEpochNumber().sendAndGet()
				: this.heartBeatService.getCurrentEpoch();
		BigInteger gasLimit = context.gasLimit;
		RawTransaction tx = RawTransaction.call(nonce, gasLimit, context.contract, context.storageLimit, epoch, context.data);
		if (this.dao.getIntConfig(KEY_TX_WAIT_EXEC, 0) == 1) {
			UsedGasAndCollateral estimate = GasTool.estimate(tx, admin.getAddress(), admin.getCfx());
			if (estimate != null) {
				logger.debug("use estimated gas {}, original {}, for {} id {}", estimate.getGasLimit(), gasLimit, data.getClass().getSimpleName(), data.getId());
				gasLimit = estimate.getGasLimit();
			}
		}

		BigInteger gasPrice = BlockchainConfig.instance.txGasPrice;
		if (resendOnError) {
			gasPrice = txRecorder.getLast().gasPrice;
			if (gasPrice == null) {
				gasPrice = BlockchainConfig.instance.txGasPrice;
			}
			gasPrice = gasPrice.add(BlockchainConfig.instance.txResendGasPriceDelta);
		}
		tx.setGasPrice(gasPrice);

		String[] txInfo;
		if (this.config.evm) {
			txInfo = evmTxService.buildTx(context.contract.getHexAddress(), nonce, context.data, gasPrice, gasLimit);
		} else {
			txInfo = CfxTxSender.buildTx(admin, context.contract, nonce, epoch, context.data, gasPrice, gasLimit, context.storageLimit);
		}
		String signedTx = txInfo[0];
		String txHash = txInfo[1];

		NonceKeeper.reserve(resendOnError, this.dao, txHash, nonce);
		data.updateSettlement(this.dao, SettlementStatus.OffChainSettled, txHash, tx);
		
		SendTransactionResult result;
		
		try (Context ctx = perfSendTx.time()) {
			// For re-send case, do not update the local tx nonce of DEX admin.
			result = admin.getCfx().sendRawTransactionAndGet(signedTx);
			if (!resendOnError) {
				admin.setNonce(nonce.add(BigInteger.ONE));
			}
			NonceKeeper.saveNext(resendOnError, this.dao, admin.getNonce().toString());
		}
		
		if (result.getRawError() != null) {
			switch (result.getErrorType()) {
			case TxAlreadyExists:
			case InvalidNonceAlreadyUsed:
				// E.g. re-send transaction due to temporary IO error.
				// Just go ahead
				logger.info("failed to send transaction to full node: reason = {}, settleable = {}, adminNonce = {}",
						result.getErrorType(), data, admin.getNonce());
				if (this.txRelayer != null) {
					this.txRelayer.submit(signedTx);
				}
				break;
				
			case TxPoolFull:
				// First of all, the dependent full node should guarantee availability.
				// Anyway, just re-settle again for such extreme case.
				logger.warn("failed to send transaction to full node: reason = {}, settleable = {}, adminNonce = {}",
						result.getErrorType(), data, admin.getNonce());
				throw BusinessException.system(result.toString());
				
			case Rlp:
			case InvalidEpochHeight:
			case InvalidChainId:
			case InvalidGasLimitExceedsMax:
			case InvalidGasLimitLessThanIntrinsic:
			case InvalidGasPriceTooSmall:
			case InvalidNonceTooStale:
			case InvalidNonceTooFuture:
			case InvalidSignature:
			case Internal:
			case Unknown:
				if (resendOnError && txRecorder.getReceipt(admin.getCfx()).isPresent()) {
					logger.info("failed to re-send transaction to full node, but previous transaction already executed: reason = {}, settleable = {}, adminNonce = {}",
							result.getErrorType(), data, admin.getNonce());
				} else {
					// If extreme unrecoverable case happened, pause system and notify administrator to involve.
					String error = String.format("failed to send transaction to full node: result = %s, settleable = %s, adminNonce = %s",
							result, data, admin.getNonce());
					logger.error(error);
					this.healthService.pause(PauseSource.Blockchain, error);
				
					// throw to settle again
					throw BusinessFault.SystemPaused.rise();
				}
				
				break;
				
			default:
				throw new Exception(String.format("failed to send transaction to full node and got unknown error: code = %s, data = %s, message = %s",
						result.getRawError().getCode(), result.getRawError().getData(), result.getRawError().getMessage()));
			}
		} else if (!result.getTxHash().equals(txHash)) {
			this.fatal(data, "tx hash mismatch: tx.hash() = %s, RPC returned = %s", txHash, result.getTxHash());
		} else if (this.txRelayer != null) {
			this.txRelayer.submit(signedTx);
		}
	}

	@Autowired
	public void mustInitFeeServiceFirst(FeeService feeService) {
		logger.info("init fee service {}", feeService.getData().getFeeRecipient());
	}
	
	@PostConstruct
	public void init() {
		logger.info("initialization started ...");
		NonceKeeper.checkNonce(logger, this.blockchain.getAdmin(), dao);
		long start = System.currentTimeMillis();
		
		/*
		 * TODO low performance in case of too many data unsettled.
		 * 
		 * If trade TPS is higher than on-chain settlement TPS all the time,
		 * there will be too many records need to be settled when DEX restarted.
		 * 
		 * In this case, DEX should support to settle all/partial pending trades 
		 * before allowing to place new orders. In addition, pagination is required 
		 * to avoid loading too many records for a SQL query.
		 * 
		 * If monitoring and alert system enabled, once too many records pending 
		 * in the queue, DEX should notify administrator to involve, and pause the
		 * system if necessary.
		 */
		
		// Continue to monitor the transaction confirmation of on chain settled items
		List<Trade> trades = this.dao.listTradesByStatus(SettlementStatus.OnChainSettled);
		int batchCount = 0;
		if (!trades.isEmpty()) {
			Settleable batch = Settleable.orderMatched(trades.get(0), feeService.getData());

			for (int i = 1, len = trades.size(); i < len; i++) {
				Settleable current = Settleable.orderMatched(trades.get(i), feeService.getData());
				
				if (batch.getSettledTxNonce() == current.getSettledTxNonce()) {
					batch.batchWith(current);
				} else {
					this.monitor.add(batch);
					batchCount++;
					batch = current;
				}
			}
			
			this.monitor.add(batch);
			batchCount++;
		}
		logger.info("continue to monitor tx confirmation for trades: count = {}, batch = {}", trades.size(), batchCount);
		
		List<CancelOrderRequest> requests = this.dao.listCancelOrderRequests(SettlementStatus.OnChainSettled);
		for (CancelOrderRequest request : requests) {
			this.monitor.add(Settleable.orderCancelled(request.getOrderId(), request.getTxHash(), request.getTxNonce()));
		}
		logger.info("continue to monitor tx confirmation for cancelled orders: {}", requests.size());
		
		List<WithdrawRecord> withdraws = this.dao.listWithdrawRecordsByStatus(SettlementStatus.OnChainSettled);
		for (WithdrawRecord record : withdraws) {
			this.monitor.add(Settleable.withdraw(record));
		}
		logger.info("continue to monitor tx confirmation for withdraws: {}", withdraws.size());
		
		List<TransferRecord> transfers = this.dao.listTransferRecordsByStatus(SettlementStatus.OnChainSettled);
		for (TransferRecord record : transfers) {
			this.monitor.add(Settleable.transfer(record));
		}
		logger.info("continue to monitor tx confirmation for transfers: {}", transfers.size());
		
		// Continue to settle the items that not settled on chain yet.
		// Note, it's important to settle items in the correct order.
		trades = this.dao.listTradesByStatus(SettlementStatus.OffChainSettled);
		HashMap<Integer, Boolean> isInstantExchange = new HashMap<Integer, Boolean>();
		for (Trade trade : trades) {
			if (!isInstantExchange.containsKey(trade.getProductId())) {
				isInstantExchange.put(trade.getProductId(), 
						this.dao.getProduct(trade.getProductId()).mustGet() instanceof InstantExchangeProduct);
			}
			if (isInstantExchange.get(trade.getProductId()))
				this.submit(Settleable.instantExchangeOrderMatched(trade));
			else
				this.submit(Settleable.orderMatched(trade, feeService.getData()));
		}
		logger.info("continue to settle on chain for trades: {}", trades.size());
		
		requests = this.dao.listCancelOrderRequests(SettlementStatus.OffChainSettled);
		for (CancelOrderRequest request : requests) {
			if (this.dao.mustGetOrder(request.getOrderId()).getStatus().equals(OrderStatus.Cancelled)) {
				this.submit(Settleable.orderCancelled(request.getOrderId(), request.getTxHash(), request.getTxNonce()));
			}
		}
		logger.info("continue to settle on chain for cancelled orders: {}", requests.size());
		
		withdraws = this.dao.listWithdrawRecordsByStatus(SettlementStatus.OffChainSettled);
		for (WithdrawRecord record : withdraws) {
			this.submit(Settleable.withdraw(record));
		}
		logger.info("continue to settle on chain for withdraws: {}", withdraws.size());
		
		transfers = this.dao.listTransferRecordsByStatus(SettlementStatus.OffChainSettled);
		for (TransferRecord record : transfers) {
			this.submit(Settleable.transfer(record));
		}
		logger.info("continue to settle on chain for transfers: {}", transfers.size());
		
		logger.info("initialization completed, elapsed = {} milliseconds", System.currentTimeMillis() - start);
	}

}
