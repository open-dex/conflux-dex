package conflux.dex.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import conflux.dex.blockchain.TypedOrder;
import conflux.dex.common.BusinessFault;
import conflux.dex.matching.PruneRequest;
import conflux.dex.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import conflux.dex.common.BusinessException;
import conflux.dex.common.channel.Sender;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.event.OrderEventArg;
import conflux.dex.matching.Signal;
import conflux.dex.model.Account;
import conflux.dex.model.AccountStatus;
import conflux.dex.model.BalanceChangeType;
import conflux.dex.model.CancelOrderReason;
import conflux.dex.model.CancelOrderRequest;
import conflux.dex.model.Currency;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.OrderType;
import conflux.dex.model.Product;
import conflux.dex.model.SettlementStatus;
import conflux.dex.ws.topic.AccountTopic;

@Service
public class OrderService {
	private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
	
	private static final int NUM_BATCH_CANCELLATION = 100;
	
	private DexDao dao;
	private Sender<Object> orderSender;
	private long lastPruneTime;
	@Value("${blockchain.prune.order.interval.update.millis:600000}")
	public long updateExpirationMillis = 600000;
	private AccountTopic topic;
	private AccountService accountService;

	@Autowired
	public OrderService(DexDao dao, Sender<Object> orderSender, AccountTopic topic, AccountService accountService) {
		this.dao = dao;
		this.orderSender = orderSender;
		this.topic = topic;
		this.accountService = accountService;
	}
	
	public OrderService(DexDao dao, Sender<Object> orderSender, AccountTopic topic) {
		this(dao, orderSender, topic, new AccountService(dao));
	}
	
	@PostConstruct
	public void init() {
		logger.info("initialization started ...");
		
		/*
		 * In case of too many orders during initialization,
		 * Administrator shall cancel all orders at first.
		 * 
		 * Otherwise, DEX should support to initialize in-completed orders
		 * before allowing to place new orders. In addition, pagination is
		 * required to avoid loading too many records for a SQL query.
		 */
		List<Order> orders = this.dao.listAllOrdersByStatus(OrderStatus.Open);
		for (Order order : orders) {
			this.sendUnhandledOrder(order);
		}
		
		if (!orders.isEmpty()) {
			logger.info("continue to open orders: {}", orders.size());
		}
		
		// For orders in canceling status, it should be a limit order.
		// Matching engine requires the order to exist in order book,
		// So, just send these orders to engine and then cancel them.
		orders = this.dao.listAllOrdersByStatus(OrderStatus.Cancelling);
		for (Order order : orders) {
			this.sendUnhandledOrder(order);
		}
		
		for (Order order : orders) {
			if (order.getType().equals(OrderType.Limit)) {
				this.orderSender.send(conflux.dex.matching.Order.cancel(order));
			}
		}
		
		if (!orders.isEmpty()) {
			logger.info("continue to cancel orders: {}", orders.size());
		}
		
		orders = this.dao.listAllOrdersByStatus(OrderStatus.New);
		for (Order order : orders) {
			this.sendUnhandledOrder(order);
		}
		
		if (!orders.isEmpty()) {
			logger.info("continue to place orders: {}", orders.size());
		}
		
		this.orderSender.send(Signal.orderImportedSignal());
		
		logger.info("initialization completed");
	}

	public TypedOrder modelOrder2typed(Order order, String userAddress) {
		Product product = this.dao.getProduct(order.getProductId()).mustGet();
		Currency baseCurrency = this.dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		Currency quoteCurrency = this.dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
		TypedOrder typedOrder = TypedOrder.from(order, userAddress, baseCurrency, quoteCurrency);
		return typedOrder;
	}
	
	private void sendUnhandledOrder(Order order) {
		this.orderSender.send(conflux.dex.matching.Order.place(order, this.dao));
	}
	
	public long placeOrder(Order order) throws BusinessException {
		long userId = order.getUserId();
		Product product = this.dao.getProduct(order.getProductId()).mustGet();
		
		Currency baseCurrency = this.dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		Currency quoteCurrency = this.dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
		
		if (product instanceof InstantExchangeProduct) {
			Product baseProduct = this.dao.getProduct(((InstantExchangeProduct)product).getBaseProductId()).mustGet();
			Currency mediumCurrency = this.dao.getCurrency(baseProduct.getBaseCurrencyId()).mustGet();
			if (((InstantExchangeProduct)product).isBaseIsBaseSide()) 
				mediumCurrency = this.dao.getCurrency(baseProduct.getQuoteCurrencyId()).mustGet();
			Optional<Account> mediumAccount = this.dao.getAccount(userId, baseCurrency.getName());
			if (!mediumAccount.isPresent()) {
				Account newMediumAccount = new Account(userId, mediumCurrency.getName(), BigDecimal.ZERO);
				this.dao.addAccount(newMediumAccount);
			}
		}
		
		Account baseAccount;
		Account quoteAccount;
		Account holdAccount;
		Account feeAccount;
		
		User feeRecipient = AccountService.getOrAddUser(dao, order.getFeeAddress());
		if (order.getSide() == OrderSide.Buy) {
			baseAccount = this.accountService.getOrAddAccount(userId, baseCurrency.getName(), BigDecimal.ZERO);
			quoteAccount = AccountService.mustGetAccount(dao, userId, quoteCurrency.getName());
			holdAccount = quoteAccount;
			feeAccount = this.accountService.getOrAddAccount(feeRecipient.getId(), baseCurrency.getName(), BigDecimal.ZERO);
		} else {
			baseAccount = AccountService.mustGetAccount(dao, userId, baseCurrency.getName());
			holdAccount = baseAccount;
			quoteAccount = this.accountService.getOrAddAccount(userId, quoteCurrency.getName(), BigDecimal.ZERO);
			feeAccount = this.accountService.getOrAddAccount(feeRecipient.getId(), quoteCurrency.getName(), BigDecimal.ZERO);
		}
		
		this.validateAccountStatus(baseAccount);
		this.validateAccountStatus(quoteAccount);
		this.validateAccountStatus(feeAccount);
		
		BigDecimal holdAmount = order.getHoldAmount();
		if (holdAccount.getAvailable().compareTo(holdAmount) < 0) {
			throw BusinessFault.AccountBalanceNotEnough.rise();
		}
		
		this.dao.execute(new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				dao.mustAddOrder(order);
				AccountService.mustUpdateAccountBalance(dao, holdAccount.getId(), holdAmount, holdAmount.negate());
			}
			
		});
		
		this.topic.publish(BalanceChangeType.OrderPlace, userId, holdAccount.getId(), true, false);
		
		this.orderSender.send(conflux.dex.matching.Order.place(order, baseAccount.getId(), quoteAccount.getId(), feeAccount.getId()));
		
		Events.PLACE_ORDER_SUBMITTED.fire(new OrderEventArg(order, product.getName()));
		
		return order.getId();
	}
	
	private void validateAccountStatus(Account account) throws BusinessException {
		if (account.getStatus().equals(AccountStatus.ForceWithdrawing)) {
			throw BusinessFault.AccountForceWithdrawing.rise();
		}
	}
	
	public void cancelOrder(long orderId) throws BusinessException {
		this.cancelOrder(CancelOrderRequest.fromSystem(orderId, CancelOrderReason.OnChainForceWithdrawRequested));
	}
	
	public void cancelOrder(CancelOrderRequest request) throws BusinessException {
		this.cancelOrder(request, false);
	}
	
	private void cancelOrder(CancelOrderRequest request, boolean includeNewStatus) throws BusinessException {
		Order cancellingOrder = this.dao.execute(new TransactionCallback<Order>() {

			@Override
			public Order doInTransaction(TransactionStatus status) {
				Order order = dao.mustGetOrderForUpdate(request.getOrderId());
				
				switch (order.getStatus()) {
				case Open:
					dao.updateOrderStatus(request.getOrderId(), OrderStatus.Open, OrderStatus.Cancelling);
					break;
				case Pending:
					dao.updateOrderStatus(request.getOrderId(), OrderStatus.Pending, OrderStatus.Cancelling);
					break;
				case New:
					if (!includeNewStatus) {
						throw new BusinessException(
								BusinessFault.OrderNotOpened.getCode(),
								String.format("cannot cancel order %s in status %s", request.getOrderId(), OrderStatus.New));
					}
					
					dao.updateOrderStatus(request.getOrderId(), OrderStatus.New, OrderStatus.Cancelled);
					request.setStatus(SettlementStatus.OnChainConfirmed);
					break;
				default:
					throw new BusinessException(
							BusinessFault.OrderNotOpened.getCode(),
							String.format("cannot cancel order %s in status %s", request.getOrderId(), order.getStatus()));
				}
				
				dao.mustAddCancelOrderRequest(request);
				
				if (request.getStatus() == SettlementStatus.OnChainConfirmed) {
					order.setStatus(OrderStatus.Cancelled);
				} else {
					order.setStatus(OrderStatus.Cancelling);
				}
				
				return order;
			}
			
		});
		
		if (cancellingOrder.getStatus() == OrderStatus.Cancelling) {
			this.orderSender.send(conflux.dex.matching.Order.cancel(cancellingOrder));	
		}
		
		Product product = this.dao.getProduct(cancellingOrder.getProductId()).mustGet();
		Events.CANCEL_ORDER_SUBMITTED.fire(new OrderEventArg(cancellingOrder, product.getName()));
	}
	
	public void cancelOrders(long userId, int currencyId) throws BusinessException {
		logger.debug("begin to cancel orders, userId = {}, currencyId = {}", userId, currencyId);
		
		List<Integer> products = this.dao.listProductsByCurrencyId(currencyId);
		logger.debug("cancel orders for {} products", products.size());
		
		for (Integer pid : products) {
			logger.debug("begin to cancel orders for product {}", pid);
			
			List<Order> openedOrders = this.dao.listOrdersByStatus(userId, pid, OrderStatus.Open, 0, Integer.MAX_VALUE, true);
			
			logger.debug("begin to cancel {} orders", openedOrders.size());
			
			for (Order order : openedOrders) {
				this.cancelOrder(order.getId());
			}
			
			logger.debug("end of cancelling {} orders", openedOrders.size());
		}
		
		logger.debug("end of cancelling orders, userId = {}, currencyId = {}", userId, currencyId);
	}
	
	public void cancelAllOrders() {
		// Opened orders already in order book
		this.orderSender.send(Signal.cancelAllOrders());
		
		List<Order> orders = this.dao.listAllOrdersByStatus(OrderStatus.Pending);
		this.cancelOrdersByAdmin(orders);
		
		orders = this.dao.listAllOrdersByStatus(OrderStatus.New);
		this.cancelOrdersByAdmin(orders);
	}
	
	private void cancelOrdersByAdmin(List<Order> orders) {
		for (Order order : orders) {
			CancelOrderRequest request = CancelOrderRequest.fromSystem(order.getId(), CancelOrderReason.AdminRequested);
			this.cancelOrder(request, true);
		}
	}
	
	public void cancelOrdersByUser(String userAddress) {
		long userId = this.dao.getUserByName(userAddress).mustGet().getId();
		OrderStatus[] validStatusList = { OrderStatus.Open, OrderStatus.Pending, OrderStatus.New };
		
		for (OrderStatus status : validStatusList) {
			int offset = 0;
			
			while (offset >= 0) {
				List<Order> orders = this.dao.listOrdersByStatus(userId, status, offset, NUM_BATCH_CANCELLATION, true);
				this.cancelOrdersByAdmin(orders);
				
				if (orders.size() < NUM_BATCH_CANCELLATION) {
					offset = -1;
				} else {
					offset += NUM_BATCH_CANCELLATION;
				}
			}
		}
	}
	
	public void cancelOrdersByProduct(String productName) {
		int productId = this.dao.getProductByName(productName).mustGet().getId();
		OrderStatus[] validStatusList = { OrderStatus.Open, OrderStatus.Pending, OrderStatus.New };
		
		for (OrderStatus status : validStatusList) {
			List<Order> orders = this.dao.listAllOrdersByStatus(productId, status);
			this.cancelOrdersByAdmin(orders);
		}
	}
	
	public void cancelOrdersByUserProduct(String userAddress, String productName) {
		long userId = this.dao.getUserByName(userAddress).mustGet().getId();
		int productId = this.dao.getProductByName(productName).mustGet().getId();
		OrderStatus[] validStatusList = { OrderStatus.Open, OrderStatus.Pending, OrderStatus.New };
		
		for (OrderStatus status : validStatusList) {
			int offset = 0;
			
			while (offset >= 0) {
				List<Order> orders = this.dao.listOrdersByStatus(userId, productId, status, offset, NUM_BATCH_CANCELLATION, true);
				this.cancelOrdersByAdmin(orders);
				
				if (orders.size() < NUM_BATCH_CANCELLATION) {
					offset = -1;
				} else {
					offset += NUM_BATCH_CANCELLATION;
				}
			}
		}
	}


	@Scheduled(
			initialDelayString = "${blockchain.prune.order.interval.update.millis:1000}",
			fixedDelayString = "${blockchain.prune.order.interval.update.millis:600000}")
	public void updateOrderExpiration() {
		long pruneTime = System.currentTimeMillis() - this.updateExpirationMillis;
		PruneRequest request = PruneRequest.create(this.lastPruneTime, pruneTime);

		this.orderSender.send(request);

		this.lastPruneTime = pruneTime;
	}
}
