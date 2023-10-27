package conflux.dex.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import conflux.dex.blockchain.EventBlockchain;
import conflux.dex.config.AuthRequire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import conflux.dex.common.BusinessFault;
import conflux.dex.common.Validators;
import conflux.dex.controller.request.AddInstantExchangeProductRequest;
import conflux.dex.controller.request.AddProductRequest;
import conflux.dex.controller.request.ChangeProductOpenStatusRequest;
import conflux.dex.controller.request.DailyLimitRateRequest;
import conflux.dex.controller.request.DailyLimitRequest;
import conflux.dex.controller.request.UpdateProductRequest;
import conflux.dex.dao.DexDao;
import conflux.dex.model.Currency;
import conflux.dex.model.DailyLimit;
import conflux.dex.model.DailyLimitRate;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.PagingResult;
import conflux.dex.model.Product;
import conflux.dex.service.DailyLimitService;
import conflux.dex.service.EngineService;

/**
 * Product management
 */
@RestController
@RequestMapping("/products")
public class ProductController {
	
	private DexDao dao;
	private DailyLimitService dailyLimitService;
	private EngineService service;
	
	@Value("${ui.admin.address}")
	private String adminAddress;
	
	private Object lock = new Object();
	@Autowired
	EventBlockchain eventBlockchain;
	
	@Autowired
	public ProductController(DexDao dao, EngineService service, DailyLimitService dailyLimitService) {
		this.dao = dao;
		this.service = service;
		this.dailyLimitService = dailyLimitService;
	}

	/**
	 * Add product
	 * Create a new product. Note, administrator privilege is required.
	 * @ignore
	 */
	@PostMapping
	@AuthRequire
	public Product add(@RequestBody AddProductRequest request) {
		request.validate(this.dao);
		
		Product product = request.product;

		synchronized (this.lock) {
			this.dao.getProductByName(product.getName()).expectNotFound(BusinessFault.ProductAlreadyExists);
			this.dao.addProduct(product);
		}
		
		// notify event service to fetch deposit.
		eventBlockchain.addAddress(this.dao.getCurrency(product.getBaseCurrencyId()).mustGet().getContractAddress(), true);
		eventBlockchain.addAddress(this.dao.getCurrency(product.getQuoteCurrencyId()).mustGet().getContractAddress(), true);
		// add engine if everything goes well.
		this.service.addEngine(product);
		return product;
	}

	/**
	 * @ignore
	 * @param request
	 */
	@PostMapping("/update-product")
	@AuthRequire
	public void update(@RequestBody UpdateProductRequest request) {
		Product newProduct = request.validate(this.dao);
		
		this.dao.updateProduct(newProduct);
	}

	/**
	 * Add instant exchange product
	 * Create a new instant exchange product. Note, administrator privilege is required.
	 * @ignore
	 */
	@PostMapping("/instantExchange")
	@AuthRequire
	public Product add(@RequestBody AddInstantExchangeProductRequest request) {
		request.validate(this.dao);
		
		InstantExchangeProduct product = request.product;

		synchronized (this.lock) {
			this.dao.getProductByName(product.getName()).expectNotFound(BusinessFault.ProductAlreadyExists);
			this.validateInstantExchangeProduct((InstantExchangeProduct) product);
			this.dao.addInstantExchangeProduct((InstantExchangeProduct) product);
		}
		
		this.service.addInstantExchangeEngine((InstantExchangeProduct) product);
		
		return product;
	}

	/**
	 * Change daily limit setting of a product
	 * Change daily limit setting of a product. Old daily limit setting will be dropped. Note, administrator privilege is required.
	 * @ignore
	 */
	@PostMapping("/dailyLimit")
	@AuthRequire
	public void add(@RequestBody DailyLimitRequest request) {
		Product product = this.dao.getProductByName(request.product).mustGet();
		int productId = product.getId();

		synchronized (this.lock) {
			// remove scheduled task
			this.dailyLimitService.removeScheduledTask(productId);
			this.dao.execute(new TransactionCallbackWithoutResult() {
				@Override
				protected void doInTransactionWithoutResult(TransactionStatus status) {
					// remove old setting in database
					dao.removeDailyLimit(productId);
					// add new setting in database
					int n = request.startTimes.size();
					for (int i = 0; i < n; ++i) {
						DailyLimit dailyLimit = DailyLimit.newDailyLimit(productId, request.startTimes.get(i), request.endTimes.get(i));
						dao.addDailyLimit(dailyLimit);
					}
				}
			});
			// close trading 
			this.dailyLimitService.sendDailyLimitOperation(productId, false);
			// reschedule tasks
			this.dailyLimitService.setupProduct(productId);
		}
	}

	/**
	 * @ignore
	 * @param request
	 */
	@PostMapping("/dailyLimit/changeStatus")
	@AuthRequire
	public void changeProductOpenStatus(@RequestBody ChangeProductOpenStatusRequest request) {
		Product product = this.dao.getProductByName(request.product).mustGet();
		int productId = product.getId();
		synchronized (this.lock) { 
			this.dailyLimitService.sendDailyLimitOperation(productId, request.open);
		}
	}
	
	/**
	 * Add or update daily limit rate for a product
	 * Add or update daily limit rate for a product. Note, administrator privilege is required.
	 * @ignore
	 */
	@PostMapping("/dailyLimitRate")
	@AuthRequire
	public void addRate(@RequestBody DailyLimitRateRequest request) {
		Product product = this.dao.getProductByName(request.product).mustGet();
		int productId = product.getId();
		DailyLimitRate dailyLimitRate = DailyLimitRate.newDailyLimitRate(productId, request.upperLimitRate, request.lowerLimitRate, request.initialPrice);
		
		this.dao.getProduct(dailyLimitRate.getProductId()).mustGet();
		
		this.dao.addDailyLimitRate(dailyLimitRate);
	}

	/**
	 * @ignore
	 * @return
	 */
	@PostMapping("/list-daily-limit")
	public List<DailyLimit> listDailyLimit() {
		return dao.listAllDailyLimit();
	}


	/**
	 * @ignore
	 * @return
	 */
	@PostMapping("/list-daily-limit-rate")
	public List<DailyLimitRate> listDailyLimitRate() {
		return dao.listDailyLimitRate();
	}
	
	public void validateInstantExchangeProduct(InstantExchangeProduct product) {
		Currency baseCurrency = this.dao.getCurrency(product.getBaseCurrencyId()).mustGet();
		Currency quoteCurrency = this.dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
		Product baseProduct = this.dao.getProduct(product.getBaseProductId()).mustGet();
		Product quoteProduct = this.dao.getProduct(product.getQuoteProductId()).mustGet();
		// sub-products can not be instant exchange product
		if (baseProduct instanceof InstantExchangeProduct || quoteProduct instanceof InstantExchangeProduct)
			throw BusinessFault.ProductNotMatch.rise();
		// validate sub-products currency matching
		int mediumCurrencyId;
		if (baseProduct.getBaseCurrencyId() == baseCurrency.getId()) {
			product.setBaseIsBaseSide(true);
			mediumCurrencyId = baseProduct.getQuoteCurrencyId();
		} else if (baseProduct.getQuoteCurrencyId() == baseCurrency.getId()) {
			product.setBaseIsBaseSide(false);
			mediumCurrencyId = baseProduct.getBaseCurrencyId();
		} else {
			throw BusinessFault.ProductNotMatch.rise();
		}
		int tmp;
		if (quoteProduct.getBaseCurrencyId() == quoteCurrency.getId()) {
			product.setQuoteIsBaseSide(true);
			tmp = quoteProduct.getQuoteCurrencyId();
		} else if (quoteProduct.getQuoteCurrencyId() == quoteCurrency.getId()) {
			product.setQuoteIsBaseSide(false);
			tmp = quoteProduct.getBaseCurrencyId();
		} else {
			throw BusinessFault.ProductNotMatch.rise();
		}
		if (mediumCurrencyId != tmp) {
			throw BusinessFault.ProductNotMatch.rise();
		}
	}
	
	/**
	 * Get product
	 * Get product of specified product name or id.
	 * @param nameOrId product name or id.
	 */
	@GetMapping("/{nameOrId}")
	public Product get(@PathVariable String nameOrId) {
		Optional<Integer> id;
		
		try {
			id = Optional.of(Integer.parseInt(nameOrId));
		} catch (NumberFormatException e) {
			id = Optional.empty();
		}
		
		if (id.isPresent()) {
			return this.dao.getProduct(id.get()).mustGet();
		}
		
		Validators.validateName(nameOrId, Product.MAX_LEN, "product name");
		
		return this.dao.getProductByName(nameOrId).mustGet();
	}
	
	/**
	 * Get products
	 * List products that ordered by name.
	 * @param offset offset to fetch products.
	 * @param limit limit to fetch products. ([1, 20])
	 */
	@GetMapping
	public ProductPagingResult list(
			@RequestParam(required = false, defaultValue = "true") Boolean filter,
			@RequestParam(required = false, defaultValue = "0") int offset,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		Validators.validatePaging(offset, limit, 20);
		// use cache from DAO, and filter in memory.
		List<Product> all = this.dao.listProducts();
		if (filter) {
			all = all.stream().filter(Product::getEnable).collect(Collectors.toList());
		}
		PagingResult<Product> paging = PagingResult.fromList(offset, limit, all);
		return new ProductPagingResult(paging);
	}
	
	/**
	 * Get daily limit
	 * List daily limit of specified product name.
	 * @param name product name.
	 */
	@GetMapping("/dailylimits/{name}")
	public List<DailyLimit> listDailyLimits(@PathVariable String name) {
		Validators.validateName(name, Product.MAX_LEN, "product name");
		
		int productId = this.dao.getProductByName(name).mustGet().getId();
		
		return this.dao.listDailyLimitsByProductId(productId);
	}
	
	/**
	 * Get daily limit rate
	 * Get daily limit rate of specified product name.
	 * @param name product name.
	 */
	@GetMapping("/dailylimitrate/{name}")
	public DailyLimitRate getDailyLimitRate(@PathVariable String name) {
		Validators.validateName(name, Product.MAX_LEN, "product name");
		
		int productId = this.dao.getProductByName(name).mustGet().getId();
		
		Optional<DailyLimitRate> rate = this.dao.getDailyLimitRateByProductId(productId);
		
		return rate.isPresent() ? rate.get() : null;
	}

}

class ProductPagingResult {
	/**
	 * Total number of products.
	 */
	public int total;
	/**
	 * Fetched products.
	 */
	public List<Product> items;
	
	public ProductPagingResult(PagingResult<Product> result) {
		this.total = result.getTotal();
		this.items = result.getItems();
	}
}
