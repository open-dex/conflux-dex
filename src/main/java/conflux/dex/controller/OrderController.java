package conflux.dex.controller;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableMap;
import conflux.dex.common.*;
import conflux.dex.config.AuthRequire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codahale.metrics.annotation.Timed;

import conflux.dex.blockchain.TypedOrder;
import conflux.dex.blockchain.TypedOrderCancellation;
import conflux.dex.dao.DexDao;
import conflux.dex.model.CancelOrderRequest;
import conflux.dex.model.Currency;
import conflux.dex.model.EIP712Data;
import conflux.dex.model.Order;
import conflux.dex.model.OrderFilter;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.OrderType;
import conflux.dex.model.PagingResult;
import conflux.dex.model.Product;
import conflux.dex.model.Trade;
import conflux.dex.model.User;
import conflux.dex.service.OrderService;
import conflux.dex.service.PlaceOrderRequest;
import conflux.web3j.types.AddressType;

/**
 * Order API
 */
@RestController
@RequestMapping("/orders")
public class OrderController {
	private Logger log = LoggerFactory.getLogger(getClass());
	private DexDao dao;
	private OrderService service;
	
	private long slowDownUserId = 0;
	private int slowDownMS = 0;

	@Autowired
	public OrderController(DexDao dao, OrderService service) {
		this.dao = dao;
		this.service = service;
		//
		this.slowDownUserId = Long.parseLong(dao.getConfig("slowDownUserId").orElse("0"));
		this.slowDownMS = Integer.parseInt(dao.getConfig("slowDownMS").orElse("0"));
	}
	
	private static class OrderPair {
		public TypedOrder message;
		public Order order;
		
		public OrderPair(TypedOrder message, Order order) {
			this.message = message;
			this.order = order;
		}
	}
	
	private OrderPair convert(PlaceOrderRequest request, boolean ignoreSignature) throws BusinessException {
		Validators.validateName(request.getProduct(), Product.MAX_LEN, "product");
		
		Product product = this.dao.getProductByName(request.getProduct()).mustGet();
		request.validate(product, ignoreSignature);
		
		User user = this.dao.getUserByName(request.getAddress()).mustGet();
		Order order = request.toOrder(product.getId(), user.getId());
		
		if (order.getClientOrderId() != null) {
			Optional<Order> existing = this.dao.getOrderByClientOrderId(user.getId(), order.getClientOrderId());
			if (existing.isPresent()) {
				throw BusinessFault.OrderClientIdAlreadyExists.rise();
			}
		}
		
		Currency baseCurrency = this.dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		Currency quoteCurrency = this.dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
		TypedOrder message = TypedOrder.from(order, user.getName(), baseCurrency, quoteCurrency);
		
		return new OrderPair(message, order);
	}
	
	/**
	 * Place order
	 * Place a new order and send to the exchange to be matched.
	 */
	@PostMapping("/place")
	@Timed(name = "order.place")
	public long place(@RequestBody PlaceOrderRequest request) throws BusinessException, IOException {
		/*
		if ("FC-USDT".equalsIgnoreCase(request.getProduct())) {
			throw BusinessException.validateFailed("FC-USDT is closed.");
		}
		 */
		OrderPair pair = this.convert(request, false);
		metrics(request.getProduct());
		
		String hash = pair.message.validate(request.getAddress(), request.getSignature());
		pair.order.setHash(hash);
		
		if (this.dao.getOrderByHash(pair.order.getHash()).isPresent()) {
			throw BusinessFault.RecordAlreadyExists.rise();
		}
		try {
			return this.service.placeOrder(pair.order);
		} catch (Exception e) {
			log.warn("Place order fail, product {}, user address {}, error {}",
					request.getProduct(), request.getAddress(), e.getMessage());
			throw e;
		}
	}

	private ConcurrentHashMap<String, Timer> productTimer = new ConcurrentHashMap<>();
	private void metrics(String productName) {
		productTimer.computeIfAbsent(productName, name-> Metrics.timer(OrderController.class, name))
				// we only care the rate of placing order, so use 1s.
				.update(1, TimeUnit.SECONDS);
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/set-slowdown")
	public Map<Object, Object> setSlowdown(long userId, int ms) {
		if (userId >= 0) {
			this.slowDownUserId = userId;
			this.slowDownMS = ms;
			this.dao.setConfig("slowDownUserId", String.valueOf(slowDownUserId));
			this.dao.setConfig("slowDownMS", String.valueOf(slowDownMS));
		}
		return ImmutableMap.builder()
				.put("slowDownUserId", slowDownUserId)
				.put("slowDownMS", slowDownMS)
				.build();
	}

	/**
	 * Place order test
	 * Test the EIP712 message of place order request.
	 */
	@PostMapping("/place/test")
	public EIP712Data testPlace(@RequestBody PlaceOrderRequest request) throws IOException {
		OrderPair pair = this.convert(request, true);
		return pair.message.test();
	}
	
	/**
	 * Cancel order
	 * Submit a request to cancel an order.
	 * @param id order id.
	 */
	@PostMapping("/{id}/cancel")
	@Timed(name = "order.cancel")
	public void cancel(@PathVariable long id, @RequestBody CancelSingleOrderRequest request) throws BusinessException, IOException {
		Validators.validateTimestamp(request.timestamp);
		Validators.validateSignature(request.signature);
		
		Order order = this.dao.mustGetOrder(id);
		User user = this.dao.getUser(order.getUserId()).mustGet();
		
		this.cancel(order, user, request.timestamp, request.signature);
	}
	
	private void cancel(Order order, User user, long timestamp, String signature) throws IOException {
		if (order.getType().equals(OrderType.Market) && !order.getStatus().equals(OrderStatus.Pending)) {
			throw BusinessException.validateFailed("cannot cancel market order");
		}
		
		CancelOrderRequest request = CancelOrderRequest.fromUser(order.getId(), timestamp, signature);
		
		if (SignatureValidator.DEFAULT.isIgnored()) {
			this.service.cancelOrder(request);
			return;
		}

		TypedOrder typedOrder = this.service.modelOrder2typed(order, user.getName());
		TypedOrderCancellation cancellation = new TypedOrderCancellation(typedOrder, timestamp);
		cancellation.validate(user.getName(), signature);
		
		this.service.cancelOrder(request);
	}
	
	/**
	 * Cancel order by client order ID
	 * Submit a request to cancel an order by client order ID.
	 * @param address user address.
	 * @param clientOrderId client order ID.
	 */
	@PostMapping("/{address}/{clientOrderId}/cancel")
	@Timed(name = "order.cancel")
	public void cancelByClientOrderId(
			@PathVariable String address, 
			@PathVariable String clientOrderId, 
			@RequestBody CancelSingleOrderRequest request) throws BusinessException, IOException {
		this.validateClientOrderId(address, clientOrderId);
		Validators.validateTimestamp(request.timestamp);
		Validators.validateSignature(request.signature);
		
		User user = this.dao.getUserByName(address).mustGet();
		Order order = this.dao.mustGetOrderByClientOrderId(user.getId(), clientOrderId);
		
		this.cancel(order, user, request.timestamp, request.signature);
	}
	
	private void validateClientOrderId(String address, String clientOrderId) {
		Validators.validateAddress(address, AddressType.User, "address");
		
		if (clientOrderId.isEmpty()) {
			throw BusinessException.validateFailed("client order id not specified");
		}
		
		if (clientOrderId.length() > Order.MAX_CLIENT_ORDER_ID_LEN) {
			throw BusinessException.validateFailed("client order id length exceed %d", Order.MAX_CLIENT_ORDER_ID_LEN);
		}
	}
	
	/**
	 * Batch cancel orders
	 * Submit cancellation request for multiple orders at once with given ids (50 at most).
	 */
//	@PostMapping("/cancel")
//	public Map<Integer, Response> batchCancel(@RequestBody CancelOrdersRequest request) {
//		if (request.ids == null || request.ids.isEmpty()) {
//			throw BusinessException.validateFailed("order ids not specified");
//		}
//		
//		Validators.validateNumber(request.ids.size(), 1, 50, "number of orders");
//		this.eip712.validateSignature(request.signature);
//		
//		// TODO validate EIP712 signature
//		// Need to keep consistent with JS SDK & Solidity
//		if (!request.ids.isEmpty()) {
//			throw BusinessException.validateFailed("Not supported yet");
//		}
//		
//		Order order = this.dao.mustGetOrder(request.ids.get(0));
//		User user = this.dao.mustGetUser(order.getUserId());
//		Map<Integer, Response> batchResult = new HashMap<Integer, Response>();
//		
//		for (Integer id : request.ids) {
//			try {
//				this.service.cancelOrder(CancelOrderRequest.fromUser(id, user.getNonce(), request.signature));
//				batchResult.put(id, Response.success(id));
//			} catch (BusinessException e) {
//				batchResult.put(id, Response.failure(e));
//			} catch (Exception e) {
//				batchResult.put(id, Response.failure(BusinessException.internalError("failed to cancel order", e)));
//			}
//		}
//		
//		return batchResult;
//	}
	
	/**
	 * Get order
	 * @param id order id.
	 */
	@GetMapping("/{id}")
	@Timed(name = "order")
	public Order getOrder(@PathVariable long id) {
		return this.dao.mustGetOrder(id);
	}
	
	/**
	 * Get order by client order ID
	 * @param address user address.
	 * @param clientOrderId client order ID.
	 */
	@GetMapping("/{address}/{clientOrderId}")
	@Timed(name = "order")
	public Order getOrderByClientOrderId(@PathVariable String address, @PathVariable String clientOrderId) {
		this.validateClientOrderId(address, clientOrderId);
		User user = this.dao.getUserByName(address).mustGet();
		return this.dao.mustGetOrderByClientOrderId(user.getId(), clientOrderId);
	}
	
	/**
	 * Get order cancellation details
	 * @param id cancelled order id.
	 */
	@GetMapping("/{id}/cancel/detail")
	public CancelOrderRequest getOrderCancelRequest(@PathVariable long id) {
		return this.dao.mustGetCancelOrderRequest(id);
	}
	
	/**
	 * Get opened orders
	 * Get user opened orders of specified product or all products.
	 * @param address user address.
	 * @param product product name, empty for all products.
	 * @param offset offset to fetch orders.
	 * @param limit limit to fetch orders. ([1, 500])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/open")
	@Timed(name = "orders.opened")
	public List<Order> getOpenedOrders(
			@RequestParam String address,
			@RequestParam(required = false, defaultValue = "") String product,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "true") boolean asc) {
		Validators.validateAddress(address, AddressType.User, "address");
		if (!product.isEmpty()) {
			Validators.validateName(product, Product.MAX_LEN, "product");
		}
		Validators.validatePaging(offset, limit, 500);
		
		long userId = this.dao.getUserByName(address).mustGet().getId();
		
		if (product.isEmpty()) {
			return this.dao.listOrdersByStatus(userId, OrderStatus.Open, offset, limit, asc);
		} else {
			int productId = this.dao.getProductByName(product).mustGet().getId();
			return this.dao.listOrdersByStatus(userId, productId, OrderStatus.Open, offset, limit, asc);
		}
	}
	
	/**
	 * Get incompleted orders
	 * Get user incompleted orders of specified product or all products, including new submitted, 
	 * opened, pending and canceling orders.
	 * @param address user address.
	 * @param product product name, empty for all products.
	 * @param status order status, null for all incompleted status. Possible values: New, Open, Cancelling, Pending.
	 * @param offset offset to fetch orders.
	 * @param limit limit to fetch orders. ([1, 500])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/incompleted")
	@Timed(name = "orders.incompleted")
	public List<Order> getIncompletedOrders(
			@RequestParam String address,
			@RequestParam(required = false, defaultValue = "") String product,
			@RequestParam(required = false) OrderStatus status,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "true") boolean asc) {
		Validators.validateAddress(address, AddressType.User, "address");
		if (!product.isEmpty()) {
			Validators.validateName(product, Product.MAX_LEN, "product");
		}
		if (status != null && status.isCompleted()) {
			BusinessException.validateFailed("incompleted order status required");
		}
		Validators.validatePaging(offset, limit, 500);
		
		long userId = this.dao.getUserByName(address).mustGet().getId();
		
		if (status == null) {
			OrderFilter.Phase phase = OrderFilter.Phase.Incompleted;
			Timestamp start = Timestamp.from(Instant.EPOCH);
			Timestamp end = Timestamp.from(Instant.now());
			
			if (product.isEmpty()) {
				return this.dao.listOrdersByPhase(userId, phase, start, end, offset, limit, asc).getItems();
			} else {
				int productId = this.dao.getProductByName(product).mustGet().getId();
				return this.dao.listOrdersByPhase(userId, productId, phase, start, end, offset, limit, asc).getItems();
			}
		} else {
			if (product.isEmpty()) {
				return this.dao.listOrdersByStatus(userId, status, offset, limit, asc);
			} else {
				int productId = this.dao.getProductByName(product).mustGet().getId();
				return this.dao.listOrdersByStatus(userId, productId, status, offset, limit, asc);
			}
		}
	}
	
	/**
	 * Get completed orders
	 * Get user completed (filled or cancelled) orders of specified product or all products.
	 * @param address user address.
	 * @param product product name, empty for all products.
	 * @param side order side: "Buy", "Sell" or empty for both sides.
	 * @param startTimestamp start timestamp to fetch orders. Note, only support to query in recent 180 days.
	 * @param endTimestamp end timestamp to fetch orders. Note, only support to query orders of 2 days at most.
	 * @param offset offset to fetch orders.
	 * @param limit limit to fetch orders. ([1, 100])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/completed")
	@Timed(name = "orders.completed")
	public OrderPagingResult getCompletedOrders(
			@RequestParam String address,
			@RequestParam(required = false, defaultValue = "") String product,
			@RequestParam(required = false) OrderSide side,
			@RequestParam(required = false, defaultValue = "0") long startTimestamp,
			@RequestParam(required = false, defaultValue = "0") long endTimestamp,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "true") boolean asc) {
		Validators.validateAddress(address, AddressType.User, "address");
		if (!product.isEmpty()) {
			Validators.validateName(product, Product.MAX_LEN, "product");
		}
		Instant timestamps[] = Validators.validateTimeRange(startTimestamp, endTimestamp, Duration.ofDays(2), Duration.ofDays(180));
		Validators.validatePaging(offset, limit, 100);
		
		Timestamp start = Timestamp.from(timestamps[0]);
		Timestamp end = Timestamp.from(timestamps[1]);
		
		OrderFilter.SidedPhase filter = side == null
				? null
				: OrderFilter.SidedPhase.parse(side, true);
		
		long userId = this.dao.getUserByName(address).mustGet().getId();

		PagingResult<Order> orders;

		if (product.isEmpty()) {
			if (filter == null) {
				orders = this.dao.listOrdersByPhase(userId, OrderFilter.Phase.Completed, start, end, offset, limit, asc);
			} else {
				orders = this.dao.listOrdersBySidedPhase(userId, filter, start, end, offset, limit, asc);
			}
		} else {
			int productId = this.dao.getProductByName(product).mustGet().getId();
			
			if (filter == null) {
				orders = this.dao.listOrdersByPhase(userId, productId, OrderFilter.Phase.Completed, start, end, offset, limit, asc);
			} else {
				orders = this.dao.listOrdersBySidedPhase(userId, productId, filter, start, end, offset, limit, asc);
			}
		}
		
		return new OrderPagingResult(orders);
	}
	/**
	 * Get orders for monitoring, eg. inspect pending orders.
	 * @ignore
	*/
	@AuthRequire
	@GetMapping("/monitor")
	public List<Order> getOrders(
			@RequestParam(required = false, defaultValue = "") String product,
			@RequestParam(required = false, defaultValue = "Pending") OrderStatus status){
		if (status.equals(OrderStatus.Pending)){
		} else if (status.equals(OrderStatus.Open)) {
		} else {
			throw new IllegalArgumentException("Order status could only be open or pending.");
		}
		if (product.isEmpty()) {
			return dao.listAllOrdersByStatus(status);
		} else {
			int productId = this.dao.getProductByName(product).mustGet().getId();
			return dao.listAllOrdersByStatus(productId, status);
		}
	}
	/**
	 * Get orders
	 * Get user orders of specified product or all products.
	 * @param address user address.
	 * @param product product name, empty for all products.
	 * @param startTimestamp start timestamp to fetch orders. Note, only support to query in recent 180 days.
	 * @param endTimestamp end timestamp to fetch orders. Note, only support to query orders of 2 days at most.
	 * @param offset offset to fetch orders.
	 * @param limit limit to fetch orders. ([1, 100])
	 * @param asc in time ascending order.
	 */
	@GetMapping
	@Timed(name = "orders.history")
	public List<Order> getOrders(
			@RequestParam String address,
			@RequestParam(required = false, defaultValue = "") String product,
			@RequestParam(required = false, defaultValue = "0") long startTimestamp,
			@RequestParam(required = false, defaultValue = "0") long endTimestamp,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "true") boolean asc) {
		Validators.validateAddress(address, AddressType.User, "address");
		if (!product.isEmpty()) {
			Validators.validateName(product, Product.MAX_LEN, "product");
		}
		Instant timestamps[] = Validators.validateTimeRange(startTimestamp, endTimestamp, Duration.ofDays(2), Duration.ofDays(180));
		Validators.validatePaging(offset, limit, 100);
		
		Timestamp start = Timestamp.from(timestamps[0]);
		Timestamp end = Timestamp.from(timestamps[1]);
		long userId = this.dao.getUserByName(address).mustGet().getId();
		
		if (product.isEmpty()) {
			return this.dao.listOrdersByTimeRange(userId, start, end, offset, limit, asc);
		} else {
			int productId = this.dao.getProductByName(product).mustGet().getId();
			return this.dao.listOrdersByTimeRange(userId, productId, start, end, offset, limit, asc);
		}
	}
	
	/**
	 * Get order trades
	 * @param id order id.
	 * @param offset offset to fetch trades.
	 * @param limit limit to fetch trades. ([1, 100])
	 */
	@GetMapping("/{id}/matches")
	@Timed(name = "trades.order")
	public List<Trade> getTradesByOrderId(
			@PathVariable long id,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		Validators.validatePaging(offset, limit, 100);
		return this.dao.listTradesByOrderId(id, offset, limit);
	}
	
	/**
	 * Get trades
	 * @param address user address.
	 * @param product product name.
	 * @param startTimestamp start timestamp to fetch orders. Note, only support to query in recent 61 days.
	 * @param endTimestamp end timestamp to fetch orders. Note, only support to query orders of 2 days at most.
	 * @param offset offset to fetch trades.
	 * @param limit limit to fetch trades. ([1, 100])
	 * @param asc in time ascending order.
	 */
	@GetMapping("/matches")
	@Timed(name = "trades.product")
	public List<Trade> getTrades(
			@RequestParam String address,
			@RequestParam String product,
			@RequestParam(required = false, defaultValue = "0") long startTimestamp,
			@RequestParam(required = false, defaultValue = "0") long endTimestamp,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "100") int limit,
			@RequestParam(required = false, defaultValue = "true") boolean asc) {
		Validators.validateAddress(address, AddressType.User, "address");
		Validators.validateName(product, Product.MAX_LEN, "product");
		Instant timestamps[] = Validators.validateTimeRange(startTimestamp, endTimestamp, Duration.ofDays(2), Duration.ofDays(61));
		Validators.validatePaging(offset, limit, 100);
		
		Timestamp start = Timestamp.from(timestamps[0]);
		Timestamp end = Timestamp.from(timestamps[1]);
		long userId = this.dao.getUserByName(address).mustGet().getId();
		int productId = this.dao.getProductByName(product).mustGet().getId();
		
		return this.dao.listTradesByUser(userId, productId, start, end, offset, limit, asc);
	}

}

class CancelSingleOrderRequest {
	/**
	 * UNIX time in milliseconds.
	 */
	public long timestamp;
	/**
	 * Signature of the order cancellation request
	 */
	public String signature;
}

class OrderPagingResult {
	/**
	 * Total number of orders.
	 */
	public int total;
	/**
	 * Fetched orders.
	 */
	public List<Order> items;
	
	public OrderPagingResult(PagingResult<Order> result) {
		this.total = result.getTotal();
		this.items = result.getItems();
	}
}
