package conflux.dex.controller;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.config.AuthRequire;
import conflux.dex.service.BlockchainService;
import conflux.dex.tool.SpringTool;
import conflux.web3j.types.Address;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import conflux.dex.common.BusinessException;
import conflux.dex.common.BusinessFault;
import conflux.dex.common.Validators;
import conflux.dex.controller.request.AddCurrencyRequest;
import conflux.dex.controller.request.UpdateCurrencyRequest;
import conflux.dex.dao.CurrencyDao;
import conflux.dex.event.Events;
import conflux.dex.model.CrossChainToken;
import conflux.dex.model.Currency;
import conflux.dex.model.PagingResult;
import conflux.dex.service.ShuttleflowService;

/**
 * Currency management
 */
@RestController
@RequestMapping("/currencies")
public class CurrencyController {
	
	private CurrencyDao dao;
	private ShuttleflowService shuttleflow;
	
	@Value("${ui.admin.address}")
	private String adminAddress;
	
	@Autowired
	public CurrencyController(CurrencyDao dao) {
		this.dao = dao;
	}
	
	@Autowired
	public void setShuttleflow(ShuttleflowService shuttleflow) {
		this.shuttleflow = shuttleflow;
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/add2blockchain")
	public Currency add2blockchain(@RequestBody Currency currency) {
		BlockchainService bean = SpringTool.getBean(BlockchainService.class);
		bean.addCurrency(currency);
		return currency;
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/set-fake-fee")
	public BigDecimal setFakeFee(BigDecimal fee) {
		CrossChainToken.fakeFee = fee;
		return CrossChainToken.fakeFee;
	}

	/**
	 * @ignore
	 */
	@AuthRequire
	@PostMapping("/get-listened-address")
	public List<String> getListenedAddress() {
		BlockchainService bean = SpringTool.getBean(BlockchainService.class);
		return bean.getListenedAddress().stream().map(Address::getHexAddress).collect(Collectors.toList());
	}

	/**
	 * Add currency
	 * Create a new currency. Note, administrator privilege is required.
	 * @ignore
	 */
	@PostMapping
	@AuthRequire
	public Currency add(@RequestBody AddCurrencyRequest request) {

		Currency currency = request.currency;
		
		// load minWithdrawAmount from shuttleflow in case of cross-chain token
		if (currency.isCrossChain()) {
			Optional<CrossChainToken> token = this.shuttleflow.query(currency.getTokenAddress());
			if (!token.isPresent()) {
				throw BusinessException.validateFailed("cross-chain token not found in Shuttleflow");
			}
			
			currency.setMinimumWithdrawAmount(token.get().getMinWithdrawAmount());
		}
		
		if (!this.dao.addCurrency(currency)) {
			throw BusinessFault.CurrencyAlreadyExists.rise();
		}
		
		Events.NEW_CURRENCY_ADDED.fire(currency);
		
		return currency;
	}

	/**
	 * @ignore
	 * @param request
	 */
	@PostMapping("/update-currency")
	@AuthRequire
	public void update(@RequestBody UpdateCurrencyRequest request) {
		Currency oldCurrency = this.dao.getCurrencyByName(request.name).mustGet();
		Currency newCurrency = new Currency();
		BeanUtils.copyProperties(oldCurrency, newCurrency);
		newCurrency.setMinimumWithdrawAmount(request.minimumWithdrawAmount);
		
		this.dao.updateCurrency(newCurrency);
	}
	
	/**
	 * Get currency
	 * Get currency of specified currency name or id.
	 * @param nameOrId currency name or id.
	 */
	@GetMapping("/{nameOrId}")
	public Currency get(@PathVariable String nameOrId) {
		Optional<Integer> id;
		
		try {
			id = Optional.of(Integer.parseInt(nameOrId));
		} catch (NumberFormatException e) {
			id = Optional.empty();
		}
		
		if (id.isPresent()) {
			return this.dao.getCurrency(id.get()).mustGet();
		}
		
		Validators.validateName(nameOrId, Currency.MAX_LEN, "currency name");
		
		return this.dao.getCurrencyByName(nameOrId).mustGet();
	}
	
	/**
	 * Get currencies
	 * List currencies that ordered by name.
	 * @param offset offset to fetch currencies.
	 * @param limit limit to fetch currencies ([1, 50]).
	 */
	@GetMapping
	public CurrencyPagingResult list(
			@RequestParam(required = false, defaultValue = "0") int offset, 
			@RequestParam(required = false, defaultValue = "10") int limit) {
		Validators.validatePaging(offset, limit, 50);
		
		PagingResult<Currency> result = PagingResult.fromList(offset, limit, this.dao.listCurrencies());
		
		return new CurrencyPagingResult(result, this.shuttleflow);
	}

	/**
	 * Build contract_address.json
	 * curl -s 'http://localhost:8080/currencies/build-contract-address' | jq .data
	 * @ignore
	 */
	@GetMapping("/build-contract-address")
	public Object buildContractAddress() {
		List<Currency> list = this.dao.listCurrencies();
		Map<String, String> map = new LinkedHashMap<>(list.size() * 2+2);
		list.forEach(currency -> {
			String lowercase = currency.getName().toLowerCase();
			map.put(String.format("c%s_addr", lowercase), currency.getTokenAddress());
			map.put(String.format("l%s_addr", lowercase), currency.getContractAddress());
		});
		map.put("boomflow_addr", Domain.boomflowAddress);
		map.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
		return map;
	}
	
	/**
	 * Get cross-chain tokens
	 */
	@GetMapping("/crosschain/tokens")
	public List<CrossChainToken> listCrossChainTokens() {
		if (this.shuttleflow == null) {
			throw BusinessException.validateFailed("shuttleflow not configured");
		}
		
		return new ArrayList<CrossChainToken>(this.shuttleflow.getTokens().values());
	}

}

class CurrencyEx extends Currency {
	
	private CrossChainToken crossChainToken;
	
	public CurrencyEx(Currency currency, CrossChainToken token) {
		BeanUtils.copyProperties(currency, this);
		this.crossChainToken = token;
	}
	
	public CrossChainToken getCrossChainToken() {
		return crossChainToken;
	}
	
	public void setCrossChainToken(CrossChainToken crossChainToken) {
		this.crossChainToken = crossChainToken;
	}
	
}

class CurrencyPagingResult {
	/**
	 * Total number of currencies.
	 */
	public int total;
	/**
	 * Fetched currencies.
	 */
	public List<CurrencyEx> items;
	
	public CurrencyPagingResult(PagingResult<Currency> result, ShuttleflowService service) {
		this.total = result.getTotal();
		this.items = result.getItems().stream()
				.map(c -> new CurrencyEx(c, service.getToken(c.getId())))
				.collect(Collectors.toList());
	}
}
