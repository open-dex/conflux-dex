package conflux.dex.matching;

import conflux.dex.common.Handler;
import conflux.dex.dao.DexDao;
import conflux.dex.model.*;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Order matching engine that execute in sequence.
 * @see InstantExchangeEngine
 */
public class Engine {
	private Handler<Log> logHandler;
	private DexDao dao;
	
	public Engine(Handler<Log> logHandler, DexDao dao) {

		this.logHandler = logHandler;
		this.dao = dao;
	}

	public void doWork(Object data, int productId, OrderBook orderBook)  {
		if (data instanceof Order) {
			Order order = (Order) data;
			if (order.isCancel()) {
				this.cancelOrder(order, productId, orderBook);
			} else {
				this.placeOrder(order, orderBook);
			}
		} else if (data instanceof DailyLimitOperation) {
			this.doDailyLimitOperation((DailyLimitOperation)data, productId, orderBook);
		} else if (data instanceof Signal) {
			switch (((Signal)data).getType()) {
			case OrderImported:
				this.logHandler.handle(Log.newOrderBookInitializedLog(productId));
				break;
			case CancelAllOrders:
				this.cancelAll(productId, orderBook);
				break;
			default:
				break;
			}
		} else if (data instanceof PruneRequest) {
			this.prune((PruneRequest) data, productId, orderBook);
		}
	}
	
	private void doDailyLimitOperation(DailyLimitOperation data, int productId, OrderBook book) {
		if (data.getType() == DailyLimitOperationType.Open) {
			// open trade
			if (book.isOpen())
				return;
			book.setOpen(true);
			// update last closing price
            DexDao dao = this.dao;
            book.updateDailyLimitInfo(dao);
			// filter the maker order in invalid price range and set them to pending
			List<Log> logs = book.filterOrders();
			for (Log log : logs)
				this.logHandler.handle(log);
			// take all the pending orders by order id and do match
			List<conflux.dex.model.Order> orders = dao.listAllOrdersByStatus(productId, OrderStatus.Pending);
			Product product = dao.getProduct(productId).mustGet();
			Currency baseCurrency = dao.getCurrency(product.getBaseCurrencyId()).mustGet();
			Currency quoteCurrency = dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
			for (conflux.dex.model.Order order : orders) {
				dao.updateOrderStatus(order.getId(), OrderStatus.Pending, OrderStatus.New);
				logs = book.placeOrder(Order.place(order, dao, baseCurrency, quoteCurrency));
				for (Log log : logs)
					this.logHandler.handle(log);
			}
			this.logHandler.handle(Log.newOrderBookStatusChangedLog(productId));
		} else {
			// close trade
			if (!book.isOpen())
				return;
			book.setOpen(false);
		}
	}

	// Data flow: OrderController.placeOrder
	// -> OrderService.placeOrder
	// -> OrderSender.send (channel)
	// -> channel receive (EngineService.call)
	// -> EngineService submit (route to engine by product id)
	// -> Engine.doWork()
	private void placeOrder(Order order, OrderBook book) {
		List<Log> logs = book.placeOrder(order);
		for (Log log : logs) {
			this.logHandler.handle(log);
		}
	}

	private void cancelOrder(Order order, int productId, OrderBook book) {
		Optional<Order> cancelled = book.cancelOrder(order.getId(), order.getSide());
		if (!cancelled.isPresent()) {
			// case 1): not in depth, regard as taker order
			// case 2): cancel an other during settlement, here the order may completed.
			Log log = Log.newPendingCancelLog(productId, order);
			this.logHandler.handle(log);
			return;
		}

		Log log = Log.newCancelLog(productId, cancelled.get(), false, false);
		this.logHandler.handle(log);
	}
	
	private void cancelAll(int productId, OrderBook book) {
			this.pollOrders(OrderSide.Buy, productId, book);
			this.pollOrders(OrderSide.Sell, productId, book);
	}
	
	private void pollOrders(OrderSide side, int productId, OrderBook book) {
		while (true) {
			Optional<Order> order = book.poll(side);
			if (!order.isPresent()) {
				break;
			}
			
			Log log = Log.newCancelLog(productId, order.get(), false, true);
			this.logHandler.handle(log);
		}
	}
	
	private void prune(PruneRequest request, int productId, OrderBook book) {
		Predicate<Order> expirationPredicate = new Predicate<Order>() {

			@Override
			public boolean test(Order o) {
				return !o.isEverMatched()
						&& o.getTimestamp() >= request.getStartTimeInclusive()
						&& o.getTimestamp() < request.getEndTimeExclusive();
			}
			
		};
		
		List<Order> buyOrders = book.filter(OrderSide.Buy, expirationPredicate);
		List<Order> sellOrders = book.filter(OrderSide.Sell, expirationPredicate);
		buyOrders.addAll(sellOrders);
		
		PruneLog log = PruneLog.create(productId, request, buyOrders);
		this.logHandler.handle(log);
	}
}
