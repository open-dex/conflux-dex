package conflux.dex.matching;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import conflux.dex.common.Handler;
import conflux.dex.common.Utils;
import conflux.dex.dao.DexDao;
import conflux.dex.model.Currency;
import conflux.dex.model.DailyLimitOperation;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderStatus;

/**
 * Instant exchange order matching engine.
 * @see Engine works as e.g. btc-usdt or fc-usdt.
 * This engine works as btc->usdt then usdt->fc, in order to support btc->fc .
 * FC has daily increase/decline percentage limitation,
 * it needs a standar basement to caculate the daily INC/DEC percentage.
 */
public class InstantExchangeEngine {
	private Handler<Log> logHandler;
	private DexDao dao;
    private Set<Integer> initializedProducts = new HashSet<>();
	
	public InstantExchangeEngine(Handler<Log> logHandler, DexDao dao) {
		this.logHandler = logHandler;
		this.dao = dao;
	}
	public void doWork(Object data, InstantExchangeProduct product,
                       OrderBook baseOrderBook, OrderBook quoteOrderBook) {
		if (data instanceof Order) {
			if (((Order)data).isCancel()) {
				this.cancelOrder((Order)data, product);
			} else {
				this.placeOrder((Order)data, product, baseOrderBook, quoteOrderBook);
			}
		} else if (data instanceof DailyLimitOperation) {
			this.tryMatch(product, baseOrderBook, quoteOrderBook);
		} else if (data instanceof Signal) {
			switch (((Signal)data).getType()) {
				case OrderBookInitialized:
					if (isBothInitialized(product)) {
                        break;
                    }

					int productId = ((Signal)data).getProductId();
                    initializedProducts.add(productId);

					if (isBothInitialized(product)) {
						this.tryMatch(product, baseOrderBook, quoteOrderBook);
					}
					break;
				default:
					break;
			}
		}
	}

    private boolean isBothInitialized(InstantExchangeProduct product) {
        return this.initializedProducts.contains(product.getBaseProductId())
&& this.initializedProducts.contains(product.getQuoteProductId());
    }

    // fake order of first trade
	private Order makeFirstFakeOrder(OrderSide side, long id, BigDecimal amount, InstantExchangeProduct product) {
		Order fakeOrder;
		if (side == OrderSide.Buy) {
			// trade from quote currency to medium currency
			if (product.isQuoteIsBaseSide()) {
				fakeOrder = Order.marketSell(id, amount);
			} else {
				fakeOrder = Order.marketBuy(id, amount);
			}
		} else {
			// trade from base currency to medium currency
			if (product.isBaseIsBaseSide()) {
				fakeOrder = Order.marketSell(id, amount);
			} else {
				fakeOrder = Order.marketBuy(id, amount);
			}
		}
		return fakeOrder;
	}

	// fake order of second trade
	private Order makeLastFakeOrder(OrderSide side, long id, BigDecimal amount, InstantExchangeProduct product) {
		Order fakeOrder;
		if (side == OrderSide.Buy) {
			// trade from medium currency to base currency
			if (product.isBaseIsBaseSide()) {
				fakeOrder = Order.marketBuy(id, amount);
			} else {
				fakeOrder = Order.marketSell(id, amount);
			}
		} else {
			// trade from medium currency to quote currency
			if (product.isQuoteIsBaseSide()) {
				fakeOrder = Order.marketBuy(id, amount);
			} else {
				fakeOrder = Order.marketSell(id, amount);
			}
		}
		return fakeOrder;
	}

	// compute amount of target currency after a list of matching
	private BigDecimal computeTradedAmount(OrderSide side, List<Log> logs) {
		BigDecimal answer = BigDecimal.ZERO;
		for (Log log : logs) {
			if (log.getType() != LogType.OrderMatched)
				continue;
			BigDecimal mediumAmount = side == OrderSide.Buy ? log.getMatchAmount()
					: Utils.mul(log.getMatchAmount(), log.getMakerOrder().getPrice());
			answer = answer.add(mediumAmount);
		}
		return answer;
	}

	private void placeOrder(Order order, InstantExchangeProduct product,
                            OrderBook baseOrderBook,
                            OrderBook quoteOrderBook) {
		if (!isBothInitialized(product)) {
			InstantExchangeLog log = InstantExchangeLog.newPendingLog(product, order);
			this.logHandler.handle(log);
			return;
		}
		
		// lock the order book with smaller product id firstly to avoid deadlock
		/*
		Object lock1 = this.baseOrderBook.getLock();
		Object lock2 = this.quoteOrderBook.getLock();
		if (this.product.getBaseProductId() < this.product.getQuoteProductId()) {
			Object tmp = lock1;
			lock1 = lock2;
			lock2 = tmp;
		}
		*/
		OrderBook book1 = quoteOrderBook, book2 = baseOrderBook;
		if (order.getSide() == OrderSide.Sell) {
			// for sell, book1 is base book, book2 is quote book
			OrderBook tmp = book1;
			book1 = book2;
			book2 = tmp;
		}
		BigDecimal mediumAmount;
		List<Log> matchLogs1, matchLogs2 = null;
		boolean flag = true;
        if (!baseOrderBook.isOpen() || !quoteOrderBook.isOpen()) {
            InstantExchangeLog log = InstantExchangeLog.newPendingLog(product, order);
            this.logHandler.handle(log);
            return;
        }
        // btc-fc (btc-usdt usdt-fc) x btc provided
        // make first trade with x btc and get traded amount y usdt
        Order fakeOrder1;
        fakeOrder1 = this.makeFirstFakeOrder(order.getSide(), order.getId(), order.getAmount(), product);
        matchLogs1 = book1.tryMatch(fakeOrder1, true);
        mediumAmount = computeTradedAmount(order.getSide(), matchLogs1);
        if (fakeOrder1.getAmount().compareTo(BigDecimal.valueOf(0.001)) > 0)
            // if remain amount is greater than the threshold, treat as mismatch.
            flag = false;
        else {
            // use y usdt to do the last trade and get the traded amount z usdt
            Order fakeOrder2 = this.makeLastFakeOrder(order.getSide(), order.getId(), mediumAmount, product);
            matchLogs2 = book2.tryMatch(fakeOrder2, true);
            if (fakeOrder2.getAmount().compareTo(BigDecimal.valueOf(0.001)) > 0)
                flag = false;
            else {
                // do real match
                fakeOrder1 = this.makeFirstFakeOrder(order.getSide(), order.getId(), order.getAmount(), product);
                matchLogs1 = book1.tryMatch(fakeOrder1, false);
                mediumAmount = computeTradedAmount(order.getSide(), matchLogs1);
                fakeOrder2 = this.makeLastFakeOrder(order.getSide(), order.getId(), mediumAmount, product);
                matchLogs2 = book2.tryMatch(fakeOrder2, false);
            }
        }

        InstantExchangeLog log;
        if (flag) {
            if (order.getSide() == OrderSide.Buy)
                log = InstantExchangeLog.newInstantExchangeLog(product, order, matchLogs1, matchLogs2);
            else
                log = InstantExchangeLog.newInstantExchangeLog(product, order, matchLogs2, matchLogs1);
        } else {
            log = InstantExchangeLog.newInstantExchangeLog(product, order, null, null);
        }
        /**
         * @see conflux.dex.matching.InstantExchangeLogHandler
         * @see InstantExchangeMatchingLogWorker
         */
        this.logHandler.handle(log);
	}
	
	void tryMatch(InstantExchangeProduct product, OrderBook baseOrderBook, OrderBook quoteOrderBook) {
        if (!baseOrderBook.isOpen() || !quoteOrderBook.isOpen()) {
            return;
        }
		DexDao dao = this.dao;
		List<conflux.dex.model.Order> orders = dao.listAllOrdersByStatus(product.getId(), OrderStatus.Pending);
        Currency baseCurrency = dao.getCurrency(product.getBaseCurrencyId()).mustGet();
        Currency quoteCurrency = dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
        for (conflux.dex.model.Order order : orders) {
            dao.updateOrderStatus(order.getId(), OrderStatus.Pending, OrderStatus.New);
            this.placeOrder(Order.place(order, dao, baseCurrency, quoteCurrency), product,
                    baseOrderBook, quoteOrderBook);
        }
	}
	
	private void cancelOrder(Order order, InstantExchangeProduct product) {
		InstantExchangeLog log = InstantExchangeLog.newPendingCancelLog(product, order);
		this.logHandler.handle(log);
	}
	
}
