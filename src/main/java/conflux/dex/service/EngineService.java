package conflux.dex.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import conflux.dex.common.BusinessFault;
import conflux.dex.common.Handler;
import conflux.dex.common.Utils;
import conflux.dex.common.channel.Receiver;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.matching.Engine;
import conflux.dex.matching.InstantExchangeEngine;
import conflux.dex.matching.InstantExchangeLog;
import conflux.dex.matching.Log;
import conflux.dex.matching.Order;
import conflux.dex.matching.OrderBook;
import conflux.dex.matching.PruneRequest;
import conflux.dex.matching.Signal;
import conflux.dex.model.Currency;
import conflux.dex.model.DailyLimitOperation;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.OrderSide;
import conflux.dex.model.Product;
import conflux.dex.model.Tick;
import conflux.dex.service.blockchain.PruneOrderService;
import conflux.dex.worker.DepthAggregateManager;
import conflux.dex.worker.DepthPriceLevel;
import conflux.dex.worker.InstantExchangeMatchingLogWorker;
import conflux.dex.worker.InstantExchangeTradeSettlement;
import conflux.dex.worker.batch.BatchTradeSettlement;
import conflux.dex.worker.batch.MatchingLogBatchWorker;
import conflux.dex.worker.ticker.Ticker;

@Service
public class EngineService implements Runnable, Handler<Log> {
	private static final Logger logger = LoggerFactory.getLogger(EngineService.class);

	private int handleMatchingLogsBatchSize = 50;
	private int handleMatchlingLogsBatchSizeMax = 60;

	private DexDao dao;
	private Receiver<Object> receiver;
	private ExecutorService executor;

	private Map<Integer, Product> productMap = new ConcurrentHashMap<>();

	public final Map<Integer, MatchingLogBatchWorker> logWorkers = new ConcurrentHashMap<Integer, MatchingLogBatchWorker>();
	public final Map<Integer, InstantExchangeMatchingLogWorker> instantExchangeLogWorkers = new ConcurrentHashMap<Integer, InstantExchangeMatchingLogWorker>();
	public final Map<Integer, DepthAggregateManager> depthAggs = new ConcurrentHashMap<Integer, DepthAggregateManager>();
	public final Map<Integer, Ticker> tickers = new ConcurrentHashMap<Integer, Ticker>();
	private final Map<Integer, OrderBook> orderBooks = new ConcurrentHashMap<Integer, OrderBook>();
	private final Map<Integer, List<Integer>> instantExchangeMapping = new ConcurrentHashMap<Integer, List<Integer>>();

	private PruneOrderService orderPruneWorker;

	private Engine directEngine;
	private InstantExchangeEngine instantEngine;
	/**
	 * keep current message(task) in order to debug
	 */
	private Object currentTask;
	private HealthService healthService;
	private FeeService feeService;

	@Autowired
	public EngineService(DexDao dao, Receiver<Object> receiver, ExecutorService executor) {
		this.dao = dao;
		this.receiver = receiver;
		this.executor = executor;
	}

	@Autowired
	public void setFeeService(FeeService feeService) {
		this.feeService = feeService;
	}

	@Autowired
	public void setHealthService(HealthService healthService) {
		this.healthService = healthService;
	}

	@Autowired
	public void setHandleMatchingLogsBatchSize(@Value("${engine.log.settlement.batch.size:50}") int handleMatchingLogsBatchSize) {
		this.handleMatchingLogsBatchSize = handleMatchingLogsBatchSize;
	}

	@Autowired
	public void setHandleMatchlingLogsBatchSizeMax(@Value("${engine.log.settlement.batch.max:60}") int handleMatchlingLogsBatchSizeMax) {
		this.handleMatchlingLogsBatchSizeMax = handleMatchlingLogsBatchSizeMax;
	}

	@Autowired
	public void setOrderPruneWorker(PruneOrderService orderPruneWorker) {
		this.orderPruneWorker = orderPruneWorker;
	}

	@PostConstruct
	public void start() {
		logger.info("initialization started ...");

		// initialize engines for configured/all products
		List<Product> preloadedProducts = this.dao.listProducts();
		this.directEngine = createEngine();
		// create simple engine (and book) firstly
		for (Product product : preloadedProducts) {
			if (!(product instanceof InstantExchangeProduct)) {
				this.addEngine(product);
			}
		}
		// create cross book instant engine, dependence on previous created book.
		this.instantEngine = createInstantExchangeEngine();
		for (Product product : preloadedProducts) {
			if (product instanceof InstantExchangeProduct) {
				this.addInstantExchangeEngine((InstantExchangeProduct) product);
			}
		}
		/** @see #run */
		this.executor.submit(this);
		
		logger.info("initialization completed, products = {}", preloadedProducts.size());
	}
	
	public List<Product> getPreloadedProducts() {
		return new ArrayList<>(productMap.values());
	}

	/**
	 * Route message to engine by product id.
	 * @param productId
	 * @param data
	 */
	private void submit(int productId, Object data) {
		Product product = productMap.get(productId);
		if (product == null) {
			logger.error("unexpected data received, cannot find the engine by product id {}", productId);
		} else if (product instanceof InstantExchangeProduct) {
			InstantExchangeProduct instantProduct = (InstantExchangeProduct) product;
            OrderBook baseOrderBook = this.orderBooks.get(instantProduct.getBaseProductId());
            OrderBook quoteOrderBook = this.orderBooks.get(instantProduct.getQuoteProductId());
			instantEngine.doWork(data, instantProduct, baseOrderBook, quoteOrderBook);
		} else {
			this.directEngine.doWork(data, productId, this.orderBooks.get(productId));
		}
	}

	/**
	 * Receive message.
	 * @return
	 * @throws Exception
	 */
	@Override
	public void run(){
		try {
			runUnsafe();
		} catch (Exception e) {
			Throwable cause = e.getCause();
			if (cause instanceof InterruptedException && currentTask == null) {
				logger.warn("InterruptedException, maybe killed.");
			} else {
				healthService.pause(HealthService.PauseSource.Workers, "Exception in engine service");
				logger.error("Exception occur, task is {}", Utils.toJson(currentTask));
				logger.error("Handle task fail", e);
			}
		}
	}
	private void runUnsafe() {
		while (true) {
			Object data = null;
			try {
				data = this.receiver.receive();
			} catch (InterruptedException e) {
				break;
			}
			this.currentTask = data;
			if (data instanceof Order) {
				int productId = ((Order)data).getProductId();
				submit(productId, data);
			} else if (data instanceof DailyLimitOperation) {
				int productId = ((DailyLimitOperation)data).getProductId();
				submit(productId, data);
			} else if (data instanceof Signal) {
				switch (((Signal) data).getType()) {
					case OrderImported:
					case CancelAllOrders:
						for (int productId : this.logWorkers.keySet()) {
							this.submit(productId, data);
						}
						break;
					default:
						break;
				}
			} else if (data instanceof PruneRequest) {
                PruneRequest pruneRequest = (PruneRequest) data;
                int total = this.productMap.size();
                int index = 0;
                for (Product product : this.productMap.values()) { 
					this.submit(product.getId(), PruneRequest.create(pruneRequest, total, index));
					index++;
				}
			} else {
				logger.error("unexpected data type received: {}", data);
			}
			this.currentTask = null;
		}
	}
	
	public boolean addEngine(Product product) {
		if (this.productMap.containsKey(product.getId())) {
			return false;
		}
		createProduct(product);
		productMap.put(product.getId(), product);
		logger.info("addEngine for product {}", product.getName());
		return true;
	}

	private Engine createEngine() {
		return new Engine( this, this.dao);
	}

	private OrderBook createProduct(Product product) {
		Currency baseCurrency = this.dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		OrderBook book = new OrderBook(product.getId(), baseCurrency.getDecimalDigits());
		book.updateDailyLimitInfo(dao);
		book.setOpen(!book.isDailyLimit());
		this.orderBooks.put(product.getId(), book);
		
		DepthAggregateManager depthAgg = new DepthAggregateManager(product.getId(), product.getName(), product.getPricePrecision());
		this.depthAggs.put(product.getId(), depthAgg);
		
		Ticker ticker = new Ticker(product.getId(), this.dao);
		this.tickers.put(product.getId(), ticker);
		
		MatchingLogBatchWorker worker = new MatchingLogBatchWorker(this.executor, product, this.handleMatchingLogsBatchSize, this.handleMatchlingLogsBatchSizeMax);
		worker.addBatchHandler(new BatchTradeSettlement(this.dao, ticker, feeService));
		if (this.orderPruneWorker != null) {
			worker.addHandler(this.orderPruneWorker);
		}
		worker.addHandler(depthAgg);
		this.logWorkers.put(product.getId(), worker);
		
		Events.NEW_PRODUCT_ADDED.fire(product);
		
		logger.debug("succeed to add engine for product {}", product.getName());
		
		return book;
	}
	
	public boolean addInstantExchangeEngine(InstantExchangeProduct product) {
        if (this.productMap.containsKey(product.getId())) {
            return false;
        }
		OrderBook baseOrderBook = this.orderBooks.get(product.getBaseProductId());
		OrderBook quoteOrderBook = this.orderBooks.get(product.getQuoteProductId());
		if (baseOrderBook == null || quoteOrderBook == null) {
			throw BusinessFault.ProductOrderBookNotFound.rise();
		}
		createInstantProduct(product);
		productMap.put(product.getId(), product);
		return true;
	}

	private InstantExchangeEngine createInstantExchangeEngine() {
		return new InstantExchangeEngine(this, this.dao);
	}

	private void createInstantProduct(InstantExchangeProduct product) {
		if (!this.instantExchangeMapping.containsKey(product.getBaseProductId()))
			this.instantExchangeMapping.put(product.getBaseProductId(), new LinkedList<Integer>());

		if (!this.instantExchangeMapping.containsKey(product.getQuoteProductId()))
			this.instantExchangeMapping.put(product.getQuoteProductId(), new LinkedList<Integer>());

		this.instantExchangeMapping.get(product.getBaseProductId()).add(product.getId());
		this.instantExchangeMapping.get(product.getQuoteProductId()).add(product.getId());
		
		Ticker ticker = new Ticker(product.getId(), this.dao);
		this.tickers.put(product.getId(), ticker);

		InstantExchangeTradeSettlement tradeSettlement = new InstantExchangeTradeSettlement(this.dao, ticker);
		InstantExchangeMatchingLogWorker worker = new InstantExchangeMatchingLogWorker(this.executor, tradeSettlement, product);
		worker.addHandler(this.depthAggs.get(product.getBaseProductId()));
		worker.addHandler(this.depthAggs.get(product.getQuoteProductId()));
		this.instantExchangeLogWorkers.put(product.getId(), worker);
		
		Events.NEW_PRODUCT_ADDED.fire(product);
		
		logger.debug("succeed to add engine for instant exchange product {}", product.getName());
		
	}
	
	public EnumMap<OrderSide, List<DepthPriceLevel>> getDepth(int productId, int step, int depth) {
		DepthAggregateManager agg = this.depthAggs.get(productId);
		return agg == null ? null : agg.getLevels(step, depth);
	}
	
	public Tick getLast24HoursTick(int productId) {
		Ticker ticker = this.tickers.get(productId);
		return ticker == null ? null : ticker.getLast24HoursTick();
	}
	
	@Override
	public void handle(Log log) {
		if (log instanceof InstantExchangeLog) {
			this.instantExchangeLogWorkers.get(log.getProductId()).submit((InstantExchangeLog) log);
		} else {
			switch (log.getType()) {
			case OrderBookStatusChanged:
				List<Integer> ids = this.instantExchangeMapping.get(log.getProductId());
				if (ids != null) {
					for (int id : ids) {
						submit(id, DailyLimitOperation.openTrade(log.getProductId()));
					}
				}
				break;
			case OrderBookInitialized:
				for (int productId : this.instantExchangeLogWorkers.keySet()) {
					submit(productId, Signal.orderBookInitializedSignal(log.getProductId()));
				}
				break;
			default:
				this.logWorkers.get(log.getProductId()).submit(log);
				break;
			}
		}
	}

	public Object getCurrentTask() {
		return currentTask;
	}
}
