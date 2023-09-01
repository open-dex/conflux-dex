package conflux.dex.config;

import java.math.BigInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class BlockchainConfig extends AutoConfigBase{
	
	public static BlockchainConfig instance = new BlockchainConfig();

	@PostConstruct
	public void init() {
		instance = this;
	}
	
	@Value("${blockchain.enabled:false}")
	public boolean enabled;

	@Value("${blockchain.isEVM:false}")
	public boolean evm;
	
	// settlement configurations
	@Value("${blockchain.settlement.pause.sleep:5000}")
	public long settlementPausedSleepMillis = 5000;
	@Value("${blockchain.settlement.batch.size:10}")
	public int settlementBatchSize = 10;
	@Value("${blockchain.settlement.batch.timeout.millis:3000}")
	public long settlementBatchTimeoutMillis = 3000;
	@Value("${blockchain.settlement.nonce.check.interval:100}")
	public BigInteger settlementNonceCheckInterval = BigInteger.valueOf(100);
	@Value("${blockchain.settlement.nonce.future:1000}")
	public BigInteger settlementNonceFutureMax = BigInteger.valueOf(1000);
	@Value("${blockchain.settlement.queue.capacity:10000}")
	public int settlementQueueCapacity = 10000;
	
	// event poll configurations
	@Value("${blockchain.poll.epoch:1}")
	public BigInteger eventPollEpochFrom = BigInteger.ONE;
	@Value("${blockchain.poll.epochs.max:100}")
	public BigInteger eventPollEpochMax = BigInteger.valueOf(100);
	
	// full node transaction configurations
	@Value("${blockchain.tx.confirm.epochs.max:200}")
	public BigInteger txMaxConfirmEpochs = BigInteger.valueOf(600);
	@Value("${blockchain.tx.price:1}")
	public BigInteger txGasPrice = BigInteger.ONE;
	@Value("${blockchain.tx.price.resend.delta:1}")
	public BigInteger txResendGasPriceDelta = BigInteger.ONE;
	@Value("${blockchain.tx.limit.gas.intrinsic:60000}")
	public BigInteger txGasLimitIntrinsic = BigInteger.valueOf(60000);
	@Value("${blockchain.tx.limit.gas.trade.exec:300000}")
	public int txGasLimitTrade = 300000;
	@Value("${blockchain.tx.limit.gas.cancel.exec:150000}")
	public int txGasLimitCancel = 150000;
	@Value("${blockchain.tx.limit.gas.transfer.exec:30000}")
	public int txGasLimitTransfer = 30000;
	@Value("${blockchain.tx.limit.gas.withdraw:200000}")
	public BigInteger txGasLimitWithdraw = BigInteger.valueOf(200000);
	@Value("${blockchain.tx.limit.storage:2048}")
	public BigInteger txStorageLimit = BigInteger.valueOf(2048);
	
	// administrator configurations
	@Value("${blockchain.admin.balance.min.cfx:1000}")
	public long adminMinBalanceCfx = 1000;
	@Value("${blockchain.admin.nonce.future:1000}")
	public BigInteger adminMaxNonceFuture = BigInteger.valueOf(1000);
	
	@Value("${blockchain.tx.relayers:}")
	public String txRelayerUrls;
	
	public BigInteger batchTradeGasLimit(int count) {
		return BigInteger.valueOf(this.txGasLimitTrade * count).add(this.txGasLimitIntrinsic);
	}
	
	public BigInteger batchCancelOrdersGasLimit(int count) {
		return BigInteger.valueOf(this.txGasLimitCancel * count).add(this.txGasLimitIntrinsic);
	}
	
	public BigInteger batchTransferGasLimit(int count) {
		return BigInteger.valueOf(this.txGasLimitTransfer * count).add(this.txGasLimitIntrinsic);
	}
	
	public BigInteger batchStorageLimit(int count) {
		return BigInteger.valueOf(count).multiply(this.txStorageLimit);
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setSettlementPausedSleepMillis(long settlementPausedSleepMillis) {
		this.settlementPausedSleepMillis = settlementPausedSleepMillis;
	}

	public void setSettlementBatchSize(int settlementBatchSize) {
		this.settlementBatchSize = settlementBatchSize;
	}

	public void setSettlementBatchTimeoutMillis(long settlementBatchTimeoutMillis) {
		this.settlementBatchTimeoutMillis = settlementBatchTimeoutMillis;
	}

	public void setSettlementNonceCheckInterval(BigInteger settlementNonceCheckInterval) {
		this.settlementNonceCheckInterval = settlementNonceCheckInterval;
	}

	public void setSettlementNonceFutureMax(BigInteger settlementNonceFutureMax) {
		this.settlementNonceFutureMax = settlementNonceFutureMax;
	}

	public void setSettlementQueueCapacity(int settlementQueueCapacity) {
		this.settlementQueueCapacity = settlementQueueCapacity;
	}

	public void setEventPollEpochFrom(BigInteger eventPollEpochFrom) {
		this.eventPollEpochFrom = eventPollEpochFrom;
	}

	public void setEventPollEpochMax(BigInteger eventPollEpochMax) {
		this.eventPollEpochMax = eventPollEpochMax;
	}

	public void setTxMaxConfirmEpochs(BigInteger txMaxConfirmEpochs) {
		this.txMaxConfirmEpochs = txMaxConfirmEpochs;
	}

	public void setTxGasPrice(BigInteger txGasPrice) {
		this.txGasPrice = txGasPrice;
	}

	public void setTxResendGasPriceDelta(BigInteger txResendGasPriceDelta) {
		this.txResendGasPriceDelta = txResendGasPriceDelta;
	}

	public void setTxGasLimitIntrinsic(BigInteger txGasLimitIntrinsic) {
		this.txGasLimitIntrinsic = txGasLimitIntrinsic;
	}

	public void setTxGasLimitTrade(int txGasLimitTrade) {
		this.txGasLimitTrade = txGasLimitTrade;
	}

	public void setTxGasLimitCancel(int txGasLimitCancel) {
		this.txGasLimitCancel = txGasLimitCancel;
	}

	public void setTxGasLimitTransfer(int txGasLimitTransfer) {
		this.txGasLimitTransfer = txGasLimitTransfer;
	}

	public void setTxGasLimitWithdraw(BigInteger txGasLimitWithdraw) {
		this.txGasLimitWithdraw = txGasLimitWithdraw;
	}

	public void setTxStorageLimit(BigInteger txStorageLimit) {
		this.txStorageLimit = txStorageLimit;
	}

	public void setAdminMinBalanceCfx(long adminMinBalanceCfx) {
		this.adminMinBalanceCfx = adminMinBalanceCfx;
	}

	public void setAdminMaxNonceFuture(BigInteger adminMaxNonceFuture) {
		this.adminMaxNonceFuture = adminMaxNonceFuture;
	}

	public void setTxRelayerUrls(String txRelayerUrls) {
		this.txRelayerUrls = txRelayerUrls;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public long getSettlementPausedSleepMillis() {
		return settlementPausedSleepMillis;
	}

	public int getSettlementBatchSize() {
		return settlementBatchSize;
	}

	public long getSettlementBatchTimeoutMillis() {
		return settlementBatchTimeoutMillis;
	}

	public BigInteger getSettlementNonceCheckInterval() {
		return settlementNonceCheckInterval;
	}

	public BigInteger getSettlementNonceFutureMax() {
		return settlementNonceFutureMax;
	}

	public int getSettlementQueueCapacity() {
		return settlementQueueCapacity;
	}

	public BigInteger getEventPollEpochFrom() {
		return eventPollEpochFrom;
	}

	public BigInteger getEventPollEpochMax() {
		return eventPollEpochMax;
	}

	public BigInteger getTxMaxConfirmEpochs() {
		return txMaxConfirmEpochs;
	}

	public BigInteger getTxGasPrice() {
		return txGasPrice;
	}

	public BigInteger getTxResendGasPriceDelta() {
		return txResendGasPriceDelta;
	}

	public BigInteger getTxGasLimitIntrinsic() {
		return txGasLimitIntrinsic;
	}

	public int getTxGasLimitTrade() {
		return txGasLimitTrade;
	}

	public int getTxGasLimitCancel() {
		return txGasLimitCancel;
	}

	public int getTxGasLimitTransfer() {
		return txGasLimitTransfer;
	}

	public BigInteger getTxGasLimitWithdraw() {
		return txGasLimitWithdraw;
	}

	public BigInteger getTxStorageLimit() {
		return txStorageLimit;
	}

	public long getAdminMinBalanceCfx() {
		return adminMinBalanceCfx;
	}

	public BigInteger getAdminMaxNonceFuture() {
		return adminMaxNonceFuture;
	}

	public String getTxRelayerUrls() {
		return txRelayerUrls;
	}

	public boolean isEvm() {
		return evm;
	}

	public void setEvm(boolean evm) {
		this.evm = evm;
	}
}
