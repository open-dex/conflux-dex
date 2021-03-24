package conflux.dex.service.blockchain.settle;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.web3j.abi.datatypes.generated.Uint256;

import conflux.dex.blockchain.EncodeUtils;
import conflux.dex.blockchain.TypedOrder;
import conflux.dex.dao.DexDao;
import conflux.dex.model.Currency;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.Order;
import conflux.dex.model.Product;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.Trade;
import conflux.dex.model.User;

class InstantExchangeTradeSettlement extends Settleable {
	
	// FIXME use configured gas limit instead
	private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(300000);
	private static final BigInteger DEFAULT_STORAGE_LIMIT = BigInteger.valueOf(10000);
	
	private Trade trade;
	
	public InstantExchangeTradeSettlement(Trade trade) {
		super(trade.getId(), trade.getTxHash(), trade.getTxNonce());
		
		this.trade = trade;
	}

	@Override
	public Settleable.Context getSettlementContext(DexDao dao) throws Exception {
		if (trade.getTakerOrderId() == trade.getMakerOrderId()) {
			// special, settle
			return this.executeInstantExchangeTrade(this.trade.getTakerOrderId(), dao);
		} else {
			// record match
			return this.recordInstantExchangeMatch(this.trade, dao);
		}
	}

	@Override
	protected void update(DexDao dao, SettlementStatus status, String txHash, long txNonce) {
		dao.updateTradeSettlement(this.trade.getId(), status, txHash, txNonce);
	}
	
	private Settleable.Context recordInstantExchangeMatch(Trade trade, DexDao dao) throws Exception {
		Order takerOrder = dao.mustGetOrder(trade.getTakerOrderId());
		Order makerOrder = dao.mustGetOrder(trade.getMakerOrderId());
		User taker = dao.getUser(takerOrder.getUserId()).mustGet();
		User maker = dao.getUser(makerOrder.getUserId()).mustGet();
		Product product = dao.getProduct(trade.getProductId()).mustGet();
		Currency baseCurrency = dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		Currency quoteCurrency = dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
		Product instantExchangeProduct = dao.getProduct(takerOrder.getProductId()).mustGet();
		Currency instantExchangeBaseCurrency = dao.getCurrency(instantExchangeProduct.getBaseCurrencyId()).mustGet();
		Currency instantExchangeQuoteCurrency = dao.getCurrency(instantExchangeProduct.getQuoteCurrencyId()).mustGet();

		// Parameters: takerOrder | makerOrders | takerSig | makerSigs
		String data = EncodeUtils.encode("recordInstantExchangeOrders",
				TypedOrder.from(takerOrder, taker.getName(), instantExchangeBaseCurrency, instantExchangeQuoteCurrency),
				EncodeUtils.typedDatas2Array(TypedOrder.from(makerOrder, maker.getName(), baseCurrency, quoteCurrency)),
				EncodeUtils.hex2Bytes(takerOrder.getSignature()),
				EncodeUtils.hex2BytesArray(makerOrder.getSignature()));
		return Settleable.Context.boomflow(data, DEFAULT_GAS_LIMIT, DEFAULT_STORAGE_LIMIT);
	}
	
	private Settleable.Context executeInstantExchangeTrade(long orderId, DexDao dao) throws Exception {
		Order takerOrder = dao.mustGetOrder(orderId);
		User taker = dao.getUser(takerOrder.getUserId()).mustGet();
		InstantExchangeProduct instantExchangeProduct = (InstantExchangeProduct) dao
				.getProduct(takerOrder.getProductId()).mustGet();
		Currency instantExchangeBaseCurrency = dao.getCurrency(instantExchangeProduct.getBaseCurrencyId()).mustGet();
		Currency instantExchangeQuoteCurrency = dao.getCurrency(instantExchangeProduct.getQuoteCurrencyId()).mustGet();
		Product baseProduct = dao.getProduct(instantExchangeProduct.getBaseProductId()).mustGet();
		int mediumCurrencyId = instantExchangeProduct.isBaseIsBaseSide() ? baseProduct.getQuoteCurrencyId()
				: baseProduct.getBaseCurrencyId();
		Currency mediumCurrency = dao.getCurrency(mediumCurrencyId).mustGet();
		// Parameters: takerOrder | takerSignature | threshold
		String data = EncodeUtils.encode("executeInstantExchangeTrade",
				TypedOrder.from(takerOrder, taker.getName(), instantExchangeBaseCurrency, instantExchangeQuoteCurrency),
				EncodeUtils.hex2Bytes(takerOrder.getSignature()),
				new Uint256(mediumCurrency.toIntegerFormat(BigDecimal.valueOf(0.001))));
		return Settleable.Context.boomflow(data, DEFAULT_GAS_LIMIT, DEFAULT_STORAGE_LIMIT);
	}
	
}
