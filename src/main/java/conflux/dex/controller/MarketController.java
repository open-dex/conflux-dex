package conflux.dex.controller;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codahale.metrics.annotation.Timed;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Validators;
import conflux.dex.dao.DexDao;
import conflux.dex.model.DailyLimitRate;
import conflux.dex.model.OrderSide;
import conflux.dex.model.Product;
import conflux.dex.model.Tick;
import conflux.dex.model.Trade;
import conflux.dex.service.EngineService;
import conflux.dex.service.TickService;
import conflux.dex.service.TradeService;
import conflux.dex.worker.DepthPriceLevel;
import conflux.dex.worker.ticker.DefaultTickGranularity;
import conflux.dex.worker.ticker.TickGranularity;
import conflux.dex.worker.ticker.Ticker;

/**
 * Market data
 */
@RestController
@RequestMapping("/market")
public class MarketController {

	private final TradeService tradeService;
	private final TickService tickService;
	private Map<Integer, LatestTrade> latestTrades;
	private DexDao dao;
	private EngineService service;
	
	@Autowired
	public MarketController(DexDao dao, EngineService service, TradeService tradeService, TickService tickService) {
		this.dao = dao;
		this.service = service;
		latestTrades = new ConcurrentHashMap<>();
		this.tradeService = tradeService;
		this.tickService = tickService;
	}
	
	private static Map<String, TickGranularity> tickGranularities = new HashMap<String, TickGranularity>();
	
	static {
		for (TickGranularity granularity : Ticker.DEFAULT_GRANULARITIES) {
			tickGranularities.put(granularity.getName(), granularity);
		}
	}
	
	/**
	 * Get last trade
	 * Get the latest trade of specified product.
	 * @param product product name.
	 */
	@GetMapping("/trades/latest")
	@Timed(name = "trade.latest")
	public Trade getLastTrade(@RequestParam String product) {
		Validators.validateName(product, Product.MAX_LEN, "product");
        int productId = this.dao.getProductByName(product).mustGet().getId();
		List<Trade> trades = this.tradeService.getLatest(productId, 1);
		return trades.isEmpty() ? null : trades.get(0);
	}

    /**
	 * Get last closing price with time
     * Provide time to client with last closing price.
     * @param product
     * @return
     */
    @GetMapping("/trades/last-closing-price")
    public Map<String, Object> getLastClosingPriceWithTime(@RequestParam String product) {
        Instant localToday = DefaultTickGranularity.localToday();
        BigDecimal price = getLastClosingPrice(localToday, product);
        Map<String, Object> map = new HashMap<>(2);
        map.put("time", localToday);
        map.put("price", price);
        return map;
    }
	
	/**
	 * Get last closing price
	 * Get the latest closing price of specified product name.
	 * Deprecated, use '/trades/last-closing-price' instead.
	 * @param product product name.
	 */
	@GetMapping("/trades/latestclosingprice")
	public BigDecimal getLastClosingPrice(@RequestParam String product) {
        Instant localToday = DefaultTickGranularity.localToday();
        return getLastClosingPrice(localToday, product);
    }
    private BigDecimal getLastClosingPrice(Instant localToday, String product) {
		Validators.validateName(product, Product.MAX_LEN, "product");

		int productId = this.dao.getProductByName(product).mustGet().getId();

		// Cache last trade of yesterday, avoid DB query.
        latestTrades.computeIfAbsent(productId, k->{
            LatestTrade latestTrade = new LatestTrade();
            Optional<Trade> result = dao.getRecentTradeBefore(productId, Timestamp.from(localToday));

            if (result.isPresent()) {
                latestTrade.trade = result.get();
            } else {
                BigDecimal price = this.dao.getDailyLimitRateByProductId(productId)
                        .map(DailyLimitRate::getInitialPrice).orElse(null);
                Trade trade = new Trade();
                // build a fake trade to hold price.
                trade.setPrice(price);
                latestTrade.trade = trade;
            }
            latestTrade.instant = localToday;
            return latestTrade;
        });
		return latestTrades.get(productId).trade.getPrice();
	}
	
	/**
	 * Get trades
	 * List trades of specified product in time descending order.
	 * @param product product name.
	 * @param limit limit to fetch trades ([1:200]).
	 */
	@GetMapping("/trades")
	@Timed(name = "trades.product.recent")
	public List<Trade> listTrades(
			@RequestParam String product, 
			@RequestParam(required = false, defaultValue = "20") int limit) {
		Validators.validateName(product, Product.MAX_LEN, "product");
		Validators.validatePaging(0, limit, TradeService.MAX_LIST_SIZE);
        int productId = this.dao.getProductByName(product).mustGet().getId();
		return this.tradeService.getLatest(productId, limit);
	}
	
	/**
	 * Get ticks
	 * List ticks of specified granularity.
	 * @param product product name.
	 * @param period interval of 2 ticks. Supported values are: 1min, 5min, 15min, 30min, 60min, 1day, 1week, 1month.
	 * @param endTimestamp end timestamp to fetch ticks. Default value is current server timestamp.
	 * @param limit limit to fetch ticks ([1, 2000]).
	 */
	@GetMapping("/tickers")
	@Timed(name = "ticks")
	public List<Tick> listTicks(
			@RequestParam String product, 
			@RequestParam String period,
			@RequestParam(required = false, defaultValue = "0") long endTimestamp,
			@RequestParam(required = false, defaultValue = "150") int limit) {
		Validators.validateName(product, Product.MAX_LEN, "product");
		
		if (period == null || period.isEmpty()) {
			throw BusinessException.validateFailed("period not specified");
		}
		
		TickGranularity granularity = tickGranularities.get(period);
		if (granularity == null) {
			throw BusinessException.validateFailed("unsupported period");
		}
		
		Validators.validatePaging(0, limit, 2000);
		
		int productId = this.dao.getProductByName(product).mustGet().getId();
		Instant end;
        if (endTimestamp == 0) {
            end = Instant.now();
        } else {
            end = Instant.ofEpochMilli(Math.min(System.currentTimeMillis(), endTimestamp));
        }

        return tickService.getLatest(productId, granularity, limit, end);
	}
	
	/**
	 * Get merged tick
	 * Get the merged tick in the last 24 hours for specified product.
	 * @param product product name.
	 */
	@GetMapping("/tickers/merged/{product}")
	@Timed(name = "ticks.merged")
	public Tick getMergedTick(@PathVariable String product) {
		Validators.validateName(product, Product.MAX_LEN, "product");
		
		int productId = this.dao.getProductByName(product).mustGet().getId();
		
		return this.service.getLast24HoursTick(productId);
	}
	
	/**
	 * Batch get merged ticks
	 * Get the merged ticks in the last 24 hours for specified products in batch.
	 * @param products product names to fetch merged ticks. (20 at most)
	 */
	@GetMapping("/tickers/merged")
	@Timed(name = "ticks.merged.batch")
	public Map<String, Tick> getMergedTicks(@RequestParam("product") List<String> products) {
		Validators.validateNumber(products.size(), 1, 20, "number of products");
		
		Map<String, Tick> result = new HashMap<String, Tick>();
		
		for (String name : products) {
			Validators.validateName(name, Product.MAX_LEN, "product");
			int productId = this.dao.getProductByName(name).mustGet().getId();
			Tick tick = this.service.getLast24HoursTick(productId);
			if (tick != null) {
				result.put(name, tick);
			}
		}
		
		return result;
	}
	
	/**
	 * Get depth
	 * Get the price aggregated depth for the specified product.
	 * @param product product name.
	 * @param depth depth to fetch. ([1, 20])
	 * @param step aggregate precision. ([0, 5])
	 */
	@GetMapping("/depth")
	@Timed(name = "depth")
	public EnumMap<OrderSide, List<DepthPriceLevel>> getDepth(
			@RequestParam String product,
			@RequestParam(required = false, defaultValue = "5") int depth,
			@RequestParam(required = false, defaultValue = "0") int step) {
		Validators.validateName(product, Product.MAX_LEN, "product");
		Validators.validateNumber(depth, 1, 20, "depth");
		Validators.validateNumber(step, 0, 5, "step");
		
		int productId = this.dao.getProductByName(product).mustGet().getId();
		
		return this.service.getDepth(productId, step, depth);
	}

	/**
	 * Cache the last trade before today
	 */
	class LatestTrade {
		Trade trade;
		Instant instant;
	}
}
