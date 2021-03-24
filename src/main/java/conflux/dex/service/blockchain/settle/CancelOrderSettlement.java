package conflux.dex.service.blockchain.settle;

import java.math.BigInteger;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import conflux.dex.blockchain.EncodeUtils;
import conflux.dex.blockchain.TypedOrder;
import conflux.dex.blockchain.TypedOrderCancellation;
import conflux.dex.config.BlockchainConfig;
import conflux.dex.dao.DexDao;
import conflux.dex.model.CancelOrderReason;
import conflux.dex.model.CancelOrderRequest;
import conflux.dex.model.Currency;
import conflux.dex.model.Order;
import conflux.dex.model.Product;
import conflux.dex.model.SettlementStatus;
import conflux.dex.model.User;

class CancelOrderSettlement extends Settleable {
	
	private static final Logger logger = LoggerFactory.getLogger(CancelOrderSettlement.class);
	
	private long orderId;
	
	public CancelOrderSettlement(long orderId, String settledTxHash, long settledTxNonce) {
		super(orderId, settledTxHash, settledTxNonce);
		
		this.orderId = orderId;
	}

	@Override
	public Settleable.Context getSettlementContext(DexDao dao) throws Exception {
		Order order = dao.mustGetOrder(this.orderId);
		
		// Already fully filled in case of any unexpected issue.
		// E.g. cancel orders during trade settlement, and order status not updated to Filled.
		// So, just skip on-chain settlement for such kind of orders.
		if (order.isFullyFilled()) {
			logger.error("Unexpected order cancellation, it has already been fully filled, order id = " + this.orderId);
			return Settleable.Context.IGNORE;
		}
		
		User user = dao.getUser(order.getUserId()).mustGet();
		Product product = dao.getProduct(order.getProductId()).mustGet();
		Currency baseCurrency = dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		Currency quoteCurrency = dao.getCurrency(product.getQuoteCurrencyId()).mustGet();

		TypedOrder typedOrder = TypedOrder.from(order, user.getName(), baseCurrency, quoteCurrency);
		Optional<CancelOrderRequest> maybeRequest = dao.getCancelOrderRequest(this.orderId);
		CancelOrderRequest request = maybeRequest.isPresent()
				? maybeRequest.get()
				: CancelOrderRequest.fromSystem(this.orderId, CancelOrderReason.AdminRequested);
				
		BigInteger gasLimit = BlockchainConfig.instance.batchCancelOrdersGasLimit(1);
		BigInteger storageLimit = BlockchainConfig.instance.batchStorageLimit(1);
		String data;

		switch (request.getReason()) {
		case CustomerRequested:
			TypedOrderCancellation cancellation = new TypedOrderCancellation(typedOrder, request.getTimestamp());
			data = EncodeUtils.encode("cancelOrders",
					EncodeUtils.typedDatas2Array(cancellation),
					EncodeUtils.hex2BytesArray(request.getSignature()));
			return Settleable.Context.boomflow(data, gasLimit, storageLimit);
		case MarketOrderPartialFilled:
		case OnChainForceWithdrawRequested:
		case AdminRequested:
			data = EncodeUtils.encode("finalizeOrder", typedOrder, EncodeUtils.hex2Bytes(order.getSignature()));
			return Settleable.Context.boomflow(data, gasLimit, storageLimit);
		default:
			throw new Exception("unsupported order cancellation reason: " + request.getReason());
		}
	}

	@Override
	protected void update(DexDao dao, SettlementStatus status, String txHash, long txNonce) {
		dao.updateCancelOrderRequest(this.orderId, status, txHash, txNonce);
	}
}