package conflux.dex.service.blockchain.settle;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.util.StringUtils;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Uint256;

import com.codahale.metrics.Histogram;

import conflux.dex.blockchain.EncodeUtils;
import conflux.dex.blockchain.TypedOrder;
import conflux.dex.blockchain.crypto.legacy.RpcEncodable;
import conflux.dex.blockchain.log.FillData;
import conflux.dex.blockchain.log.TransferData;
import conflux.dex.common.Metrics;
import conflux.dex.common.Utils;
import conflux.dex.common.worker.BatchWorker.Batchable;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.dao.DexDao;
import conflux.dex.model.Currency;
import conflux.dex.model.FeeData;
import conflux.dex.model.FeeStrategy;
import conflux.dex.model.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.Product;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.Trade;
import conflux.dex.model.User;
import conflux.web3j.response.Log;
import conflux.web3j.response.Receipt;

class TradeSettlement extends Settleable {
	
	private static final Logger logger = LoggerFactory.getLogger(TradeSettlement.class);
	
	private static final Histogram batchSizeMetric = Metrics.histogram(TradeSettlement.class, "batch", "size");

	/**
	 * helpers contains more trade.
	 */
	private LinkedList<TradeSettlementHelper> helpers = new LinkedList<TradeSettlementHelper>();
	
	public TradeSettlement(Trade trade, FeeData feeData) {
		super(trade.getId(), trade.getTxHash(), trade.getTxNonce());
		
		this.helpers.add(new TradeSettlementHelper(trade, feeData));
	}

	@Override
	public Settleable.Context getSettlementContext(DexDao dao) throws Exception {
		if (this.helpers.isEmpty()) {
			return Settleable.Context.IGNORE;
		}
		
		batchSizeMetric.update(this.helpers.size());
		
		for (TradeSettlementHelper helper : this.helpers) {
			helper.initialize(dao);
		}
		
		BigInteger gasLimit = BlockchainConfig.instance.batchTradeGasLimit(this.helpers.size());
		BigInteger storageLimit = BlockchainConfig.instance.batchStorageLimit(this.helpers.size());
		
		// single trade settlement
		if (this.helpers.size() == 1) {
			TradeSettlementHelper helper = this.helpers.get(0);
			String data = EncodeUtils.encode("executeTrade",
					helper.getMakerOrder(),
					helper.getTakerOrder(),
					EncodeUtils.hex2Bytes(helper.getMakerOrderSignature()),
					EncodeUtils.hex2Bytes(helper.getTakerOrderSignature()),
					helper.getContextData());
			
			return Settleable.Context.boomflow(data, gasLimit, storageLimit);
		}
		
		// batch trade settlement
		List<TypedOrder> makerOrders = new LinkedList<TypedOrder>();
		List<TypedOrder> takerOrders = new LinkedList<TypedOrder>();
		List<String> makerOrderSignatures = new LinkedList<String>();
		List<String> takerOrderSignatures = new LinkedList<String>();
		List<TradeContext> contexts = new LinkedList<TradeContext>();
		
		for (TradeSettlementHelper helper : this.helpers) {
			makerOrders.add(helper.getMakerOrder());
			takerOrders.add(helper.getTakerOrder());
			makerOrderSignatures.add(helper.getMakerOrderSignature());
			takerOrderSignatures.add(helper.getTakerOrderSignature());
			contexts.add(helper.getContextData());
		}
		
		String data = EncodeUtils.encode("batchExecuteTrade", 
				EncodeUtils.typedDatas2Array(makerOrders),
				EncodeUtils.typedDatas2Array(takerOrders),
				EncodeUtils.hex2BytesArray(makerOrderSignatures),
				EncodeUtils.hex2BytesArray(takerOrderSignatures),
				EncodeUtils.typedDatas2Array(contexts));
		return Settleable.Context.boomflow(data, gasLimit, storageLimit);
	}

	@Override
	protected void update(DexDao dao, SettlementStatus status, String txHash, long txNonce) {
		if (this.helpers.isEmpty()) {
			return;
		}
		
		if (this.helpers.size() == 1) {
			updateTrade(helpers.get(0).trade, status, txHash, txNonce);
			dao.updateTradeSettlement(this.getId(), status, txHash, txNonce);
			return;
		}
		
		dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus s) {
				for (TradeSettlementHelper helper : helpers) {
					updateTrade(helper.trade, status, txHash, txNonce);
					dao.updateTradeSettlement(helper.trade.getId(), status, txHash, txNonce);
				}
			}
			
		});
	}

	/**
	 * update bean still in memory, in case a trade list request may reach them.
	 */
	private void updateTrade(Trade trade, SettlementStatus status, String txHash, long txNonce) {
		trade.setStatus(status);
		if (!StringUtils.isEmpty(txHash)) {
			trade.setTxHash(txHash);
			trade.setTxNonce(txNonce);
		}
	}
	
	@Override
	public boolean matches(DexDao dao, Receipt receipt) {
		int expectedLogLength = 0;
		for (TradeSettlementHelper helper : this.helpers) {
			expectedLogLength += helper.getLogLength();
		}
		
		if (!this.matchesEventLogLength(receipt, expectedLogLength, logger)) {
			return false;
		}
		
		int offset = 0;
		
		for (TradeSettlementHelper helper : this.helpers) {
			helper.initialize(dao);
			int logLength = helper.getLogLength();
			List<Log> logs = receipt.getLogs().subList(offset, offset + logLength);
			
			String error = helper.validate(logs);
			if (!StringUtils.isEmpty(error)) {
				logger.error("failed to validate receipt of trade settlement: tradeId = {}, error = {}", helper.trade.getId(), error);
				return false;
			}
			
			offset += logLength;
		}
		
		return true;
	}
	
	@Override
	public boolean isBatchable() {
		// The order of batched items/trades may be incorrect under concurrent situation.
		// Disable it before fixed.
		return false;
	}
	
	@Override
	public boolean batchWith(Batchable other) {
		if (!(other instanceof TradeSettlement)) {
			return false;
		}
		
		TradeSettlement otherTrade = (TradeSettlement) other;
		
		// 1) one is settled, but the other is unsettled;
		// 2) both are settled, but with different tx nonce;
		if (this.getSettledTxNonce() != otherTrade.getSettledTxNonce()) {
			return false;
		}
		
		this.helpers.addAll(otherTrade.helpers);
		otherTrade.helpers.clear();
		
		return true;
	}
	
	@Override
	public int size() {
		return this.helpers.size();
	}
	
	@Override
	public String toShortDisplay() {
		return String.format("%s {size=%s, status=%s, txHash=%s, txNonce=%s}",
				this.getClass().getSimpleName(), this.helpers.size(), 
				this.getStatus(), this.getSettledTxHash(), this.getSettledTxNonce());
	}
	
	@Override
	public String toString() {
		List<Long> ids = this.helpers.stream()
				.map(h -> h.trade.getId())
				.collect(Collectors.toList());
		
		return String.format("%s {size=%s, ids=%s, status=%s, txHash=%s, txNonce=%s}",
				this.getClass().getSimpleName(), this.helpers.size(), ids,
				this.getStatus(), this.getSettledTxHash(), this.getSettledTxNonce());
	}
	
}

class TradeSettlementHelper {
	
	/*
	 * 6 or 8 events emitted for a trade settlement:
	 * 1. Fill event for maker order.
	 * 2. Fill event for taker order.
	 * 3. Transfer event from maker to taker.
	 * 4. Transfer event from taker to maker.
	 * 5. Transfer event from maker to fee recipient.
	 * 6. Transfer event from taker to fee recipient.
	 * 7. [Optional] Transfer event from maker to contract fee recipient.
	 * 8. [Optional] Transfer event from taker to contract fee recipient.
	 */
	
	private static final String FEE_RECIPIENT_FOR_FREE = "0x0000000000000000000000000000000000000000";
	
	public Trade trade;
	FeeData feeData;
	
	private boolean initialized;
	
	private Order takerOrder;
	private Order makerOrder;
	private User taker;
	private User maker;
	private Product product;
	private Currency baseCurrency;
	private Currency quoteCurrency;
	
	public TradeSettlementHelper(Trade trade, FeeData feeData) {
		this.trade = trade;
		this.feeData = feeData;
	}
	
	public void initialize(DexDao dao) {
		if (this.initialized) {
			return;
		}
		
		this.takerOrder = dao.mustGetOrder(this.trade.getTakerOrderId());
		this.makerOrder = dao.mustGetOrder(this.trade.getMakerOrderId());
		this.taker = dao.getUser(takerOrder.getUserId()).mustGet();
		this.maker = dao.getUser(makerOrder.getUserId()).mustGet();
		this.product = dao.getProduct(this.trade.getProductId()).mustGet();
		this.baseCurrency = dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		this.quoteCurrency = dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
		
		this.initialized = true;
	}
	
	public int getLogLength() {
		return this.feeData.getStrategy() == FeeStrategy.ForFree ? 6 : 8;
	}
	
	public TypedOrder getMakerOrder() {
		return TypedOrder.from(this.makerOrder, this.maker.getName(), this.baseCurrency, this.quoteCurrency);
	}
	
	public TypedOrder getTakerOrder() {
		return TypedOrder.from(this.takerOrder, this.taker.getName(), this.baseCurrency, this.quoteCurrency);
	}
	
	public String getMakerOrderSignature() {
		return this.makerOrder.getSignature();
	}
	
	public String getTakerOrderSignature() {
		return this.takerOrder.getSignature();
	}
	
	private String getContractFeeAddress() {
		switch (this.feeData.getStrategy()) {
		case FeeToDex: return this.feeData.getFeeRecipient();
		case FeeToMaker: return this.maker.getName();
		default: return FEE_RECIPIENT_FOR_FREE;
		}
	}
	
	public TradeContext getContextData() {
		return new TradeContext(
				Utils.toContractValue(this.trade.getAmount()),
				Utils.toContractValue(this.feeData.getMakerFeeRate()),
				Utils.toContractValue(this.feeData.getTakerFeeRate()),
				this.getContractFeeAddress());
	}
	
	private BigDecimal getOrderFee(boolean takerRole) {
		BigDecimal contractFee = this.getContractFee(takerRole);
		BigDecimal totalFee = takerRole ? this.trade.getTakerFee() : this.trade.getMakerFee();
		return totalFee.subtract(contractFee);
	}
	
	private BigDecimal getContractFee(boolean takerRole) {
		if (takerRole) {
			BigDecimal totalFee = this.trade.getTakerFee();
			return this.feeData.getTakerFee(totalFee);
		} else {
			BigDecimal totalFee = this.trade.getMakerFee();
			return this.feeData.getMakerFee(totalFee);
		}
	}
	
	private String getBuyAsset(boolean takerRole) {
		Order order = takerRole ? this.takerOrder : this.makerOrder;
		return order.getSide() == OrderSide.Buy
				? this.baseCurrency.getContractAddress()
				: this.quoteCurrency.getContractAddress();
	}
	
	private BigDecimal getBuyAmount(boolean takerRole) {
		Order order = takerRole ? this.takerOrder : this.makerOrder;
		BigDecimal fee = takerRole ? this.trade.getTakerFee() : this.trade.getMakerFee();
		return order.getSide() == OrderSide.Buy
				? this.trade.getAmount().subtract(fee)
				: this.trade.getFunds().subtract(fee);
	}
	
	public String validate(List<Log> logs) {
		// validate logs length
		if (logs == null) {
			return "event logs is null";
		}
		
		if (logs.size() != this.getLogLength()) {
			return String.format("invalid length of event logs, expected = %s, actual = %s", this.getLogLength(), logs.size());
		}
		
		// validate Fill event log of maker order
		String error = this.validateFillEvent(logs.get(0), false);
		if (!StringUtils.isEmpty(error)) {
			return String.format("failed to validate Fill event of maker order: %s", error);
		}
		
		// validate "Fill" event log of taker order
		error = this.validateFillEvent(logs.get(1), true);
		if (!StringUtils.isEmpty(error)) {
			return String.format("failed to validate Fill event of taker order: %s", error);
		}
		
		// validate "Transfer" event log from maker to taker
		error = TransferData.validate(logs.get(2), this.getBuyAsset(true), this.maker.getName(), this.taker.getName(), this.getBuyAmount(true));
		if (!StringUtils.isEmpty(error)) {
			return String.format("failed to validate Transfer event from maker to taker: %s", error);
		}
		
		// validate "Transfer" event log from taker to maker
		error = TransferData.validate(logs.get(3), this.getBuyAsset(false), this.taker.getName(), this.maker.getName(), this.getBuyAmount(false));
		if (!StringUtils.isEmpty(error)) {
			return String.format("failed to validate Transfer event from taker to maker: %s", error);
		}
		
		// validate "Transfer" event log from maker to fee recipient
		error = TransferData.validate(logs.get(4), this.getBuyAsset(true), this.maker.getName(), this.takerOrder.getFeeAddress(), this.getOrderFee(true));
		if (!StringUtils.isEmpty(error)) {
			return String.format("failed to validate Transfer event from maker to fee recipient: %s", error);
		}
		
		// validate "Transfer" event log from taker to fee recipient
		error = TransferData.validate(logs.get(5), this.getBuyAsset(false), this.taker.getName(), this.makerOrder.getFeeAddress(), this.getOrderFee(false));
		if (!StringUtils.isEmpty(error)) {
			return String.format("failed to validate Transfer event from taker to fee recipient: %s", error);
		}
		
		// validate optional "Transfer" event log from maker to contract fee recipient
		switch (this.feeData.getStrategy()) {
		case FeeToDex:
			error = TransferData.validate(logs.get(6), this.getBuyAsset(true), this.maker.getName(), this.feeData.getFeeRecipient(), this.getContractFee(true));
			break;
		case FeeToMaker:
			error = TransferData.validate(logs.get(6), this.getBuyAsset(true), this.maker.getName(), this.maker.getName(), this.getContractFee(true));
			break;
		default:
			break;
		}
		
		if (!StringUtils.isEmpty(error)) {
			return String.format("failed to validate Transfer event from maker to contract fee recipient: %s", error);
		}
		
		// validate optional "Transfer" event log from taker to contract fee recipient
		switch (this.feeData.getStrategy()) {
		case FeeToDex:
			error = TransferData.validate(logs.get(7), this.getBuyAsset(false), this.taker.getName(), this.feeData.getFeeRecipient(), this.getContractFee(false));
			break;
		case FeeToMaker:
			error = TransferData.validate(logs.get(7), this.getBuyAsset(false), this.taker.getName(), this.maker.getName(), this.getContractFee(false));
			break;
		default:
			break;
		}
		
		if (!StringUtils.isEmpty(error)) {
			return String.format("failed to validate Transfer event from taker to contract fee recipient: %s", error);
		}
		
		return null;
	}
	
	private String validateFillEvent(Log log, boolean takerRole) {
		Optional<FillData> maybeFillData = FillData.tryParse(log);
		if (!maybeFillData.isPresent()) {
			return "failed to parse Fill event";
		}
		
		FillData data = maybeFillData.get();
		Order order = takerRole ? this.takerOrder : this.makerOrder;
		
		// validate order hash
		if (!order.getHash().equalsIgnoreCase(data.orderHash)) {
			return String.format("order hash mismatch, offChain = %s, onChain = %s", order.getHash(), data.orderHash);
		}
		
		// validate trade fee
		String error = this.validateFee(data, takerRole);
		if (!StringUtils.isEmpty(error)) {
			return error;
		}
		
		// validate contract fee address
		error = this.validateContractFeeAddress(data);
		if (!StringUtils.isEmpty(error)) {
			return error;
		}
		
		// Ignore the validation for DEX administrator address: data.matcherAddress
		// Now, it is always the DEX administrator that send transaction on chain.
		
		// trade amount on chain:
		// 1) for market buy order, it is trade funds.
		// 2) otherwise, it is trade amount.
		BigDecimal offChainTradeAmount = order.isMarketBuy() ? this.trade.getFunds() : this.trade.getAmount();
		if (Currency.toBlockchainFormat(offChainTradeAmount).compareTo(data.tradeAmount) != 0) {
			return String.format("trade amount mismatch, isMarketBuy = %s, offChain = %s, onChain = %s",
					order.isMarketBuy(), offChainTradeAmount.toPlainString(), data.tradeAmount);
		}
		
		return null;
	}
	
	private String validateFee(FillData data, boolean takerRole) {
		// validate total trade fee
		BigDecimal offChainTradeFee = takerRole ? this.trade.getTakerFee() : this.trade.getMakerFee();
		BigInteger onChainTradeFee = data.fee.add(data.contractFee);
		if (Currency.toBlockchainFormat(offChainTradeFee).compareTo(onChainTradeFee) != 0) {
			return String.format("trade total fee mismatch, offChain = %s, onChain = %s", offChainTradeFee.toPlainString(), onChainTradeFee);
		}
		
		// validate order fee
		BigDecimal orderFee = this.getOrderFee(takerRole);
		if (Currency.toBlockchainFormat(orderFee).compareTo(data.fee) != 0) {
			return String.format("order fee mismatch, offChain = %s, onChain = %s", orderFee.toPlainString(), data.fee);
		}
		
		// validate contract fee
		BigDecimal contractFee = this.getContractFee(takerRole);
		if (Currency.toBlockchainFormat(contractFee).compareTo(data.contractFee) != 0) {
			return String.format("contract fee mismatch, offChain = %s, onChain = %s", contractFee.toPlainString(), data.contractFee);
		}
		
		return null;
	}
	
	private String validateContractFeeAddress(FillData data) {
		switch (this.feeData.getStrategy()) {
		case FeeToDex:
			if (!feeData.getFeeRecipient().equalsIgnoreCase(data.contractFeeAddress)) {
				return String.format("contract fee address mismatch, offChain = %s, onChain = %s, feeData = %s",
						feeData.getFeeRecipient(), data.contractFeeAddress, this.feeData);
			}
			break;
			
		case FeeToMaker:
			if (!this.maker.getName().equalsIgnoreCase(data.contractFeeAddress)) {
				return String.format("contract fee address mismatch, offChain = %s, onChain = %s, feeData = %s",
						this.maker.getName(), data.contractFeeAddress, this.feeData);
			}
			break;
			
		default:
			if (!FEE_RECIPIENT_FOR_FREE.equalsIgnoreCase(data.contractFeeAddress)) {
				return String.format("contract fee address mismatch, offChain = %s, onChain = %s, feeData = %s",
						FEE_RECIPIENT_FOR_FREE, data.contractFeeAddress, this.feeData);
			}
			break;
		}
		
		return null;
	}

}

class TradeContext extends StaticStruct implements RpcEncodable {
	
	public BigInteger tradeAmount;
	public BigInteger makerContractFee;
	public BigInteger takerContractFee;
	public String contractFeeAddress;
	
	public TradeContext(BigInteger tradeAmount, BigInteger makerContractFee, BigInteger takerContractFee, String contractFeeAddress) {
		super(new Uint256(tradeAmount), new Uint256(makerContractFee), new Uint256(takerContractFee), new Address(contractFeeAddress));
		
		this.tradeAmount = tradeAmount;
		this.makerContractFee = makerContractFee;
		this.takerContractFee = takerContractFee;
		this.contractFeeAddress = contractFeeAddress;
	}

	@Override
	public List<Object> toArray() {
		return Arrays.asList(
				this.tradeAmount,
				this.makerContractFee,
				this.takerContractFee,
				this.contractFeeAddress);
	}
	
}
