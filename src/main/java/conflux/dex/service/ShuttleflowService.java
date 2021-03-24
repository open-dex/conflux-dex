package conflux.dex.service;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import conflux.dex.controller.AddressTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;

import conflux.dex.common.BusinessException;
import conflux.dex.common.Utils;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.CrossChainToken;
import conflux.dex.model.Currency;
import conflux.web3j.Cfx;
import conflux.web3j.RpcException;
import conflux.web3j.contract.ContractCall;
import conflux.web3j.contract.abi.DecodeUtil;

/**
 * Auto update cross chain token configurations.
 * It's not needed if you use tokens already supported by shuttle flow.
 */
@Service
public class ShuttleflowService {
	
	private static Logger logger = LoggerFactory.getLogger(ShuttleflowService.class);

	private ContractCall call;
	private DexDao dao;
	public boolean disabled = true;
	
	private Map<Integer, CrossChainToken> tokens = new ConcurrentHashMap<>();
	
	@Autowired
	public ShuttleflowService(Cfx cfx, DexDao dao,
			@Value("${CUSTODIAN_PROXY_ADDRESS:NOT_USE}") String custodianProxyAddress) {
		if (disabled) {
			return;
		}
		logger.info("CUSTODIAN_PROXY_ADDRESS => {}", custodianProxyAddress);
		this.call = new ContractCall(cfx, AddressTool.address(custodianProxyAddress));
		this.dao = dao;
		
		Events.NEW_CURRENCY_ADDED.addHandler(this::addOrUpdateCrossChainToken);
	}
	
	public Map<Integer, CrossChainToken> getTokens() {
		return tokens;
	}
	
	public CrossChainToken getToken(int currencyId) {
		return this.tokens.get(currencyId);
	}
	
	@PostConstruct
	public void init() {
		if (disabled) {
			return;
		}
		List<Currency> currencies = this.dao.listCurrencies();
		logger.info("currencies size {}", currencies.size());
		currencies.stream()
			.filter(t -> t.isCrossChain())
			.forEach(this::addOrUpdateCrossChainToken);
		if (tokens.isEmpty()) {
			throw new IllegalArgumentException("Cross chain tokens are empty, please check custodian proxy address.");
		}
	}
	
	private void addOrUpdateCrossChainToken(Currency currency) {
		if (!currency.isCrossChain()) {
			logger.info("not cross chain token {}", currency.getName());
			return;
		}
		
		Optional<CrossChainToken> maybeToken = this.query(currency.getTokenAddress());
		if (!maybeToken.isPresent()) {
			return;
		}
		
		CrossChainToken token = maybeToken.get();
		
		if (tokens.put(currency.getId(), token) == null) {
			logger.info("cross-chain token updated: {}", token);
		} else {
			logger.debug("cross-chain token updated: {}", token);
		}
	}
	
	public Optional<CrossChainToken> query(String erc777) {
		CrossChainToken token = new CrossChainToken();
		token.setAddress(erc777);
		
		// token decimals
		Address tokenAddress = new Address(erc777);
		String encoded = this.call.call("token_decimals", tokenAddress).sendAndGet();
		if (StringUtils.isEmpty(encoded)) {
			logger.warn("token_decimals is null, {}", erc777);
			return Optional.empty();
		}
		BigInteger decoded = DecodeUtil.decode(encoded, Uint8.class);
		if (decoded == null) {
			logger.warn("decoded token_decimals is null, {}, raw values [{}]", erc777, encoded);
			return Optional.empty();
		}
		int decimals = decoded.intValueExact();
		if (decimals == 0) {
			logger.warn("decimal is zero. {}", erc777);
			return Optional.empty();
		}
		token.setDecimals(decimals);
		
		// token name: btc, eth or ERC20 address
		encoded = this.call.call("token_reference", tokenAddress).sendAndGet();
		String name = DecodeUtil.decode(encoded, Utf8String.class);
		if (StringUtils.isEmpty(name)) {
			logger.warn("name is empty. {}", erc777);
			return Optional.empty();
		}
		token.setName(name);
		
		// withdraw fee
		Utf8String tokenName = new Utf8String(name);
		encoded = this.call.call("burn_fee", tokenName).sendAndGet();
		BigInteger burnFee = DecodeUtil.decode(encoded, Uint256.class);
		token.setWithdrawFee(Utils.fromContractValue(burnFee, decimals));
		
		// minimum withdraw amount
		if (Currency.BTC.equalsIgnoreCase(name)) {
			encoded = this.call.call("btc_minimal_burn_value").sendAndGet();
		} else {
			encoded = this.call.call("minimal_burn_value", tokenName).sendAndGet();
		}
		BigInteger minBurnAmount = DecodeUtil.decode(encoded, Uint256.class);
		token.setMinWithdrawAmount(Utils.fromContractValue(minBurnAmount, decimals));
		
		return Optional.of(token);
	}
	
	public boolean reload() throws BusinessException {
		if (disabled) {
			return false;
		}
		boolean changed = false;
		List<Currency> currencies = this.dao.listCurrencies();
		
		for (Currency currency : currencies) {
			if (this.updateCrossChainToken(currency)) {
				changed = true;
			}
		}
		
		return changed;
	}
	
	private boolean updateCrossChainToken(Currency currency) throws BusinessException {
		if (!currency.isCrossChain()) {
			return false;
		}
		
		CrossChainToken token = this.tokens.get(currency.getId());
		if (token == null) {
			return false;
		}
		
		// get latest token address by name
		Utf8String tokenName = new Utf8String(token.getName());
		String encoded = this.call.call("getReferenceToken", tokenName).sendAndGet();
		String erc777 = DecodeUtil.decode(encoded, Address.class);
		
		Optional<CrossChainToken> maybeToken = this.query(erc777);
		if (!maybeToken.isPresent()) {
			throw BusinessException.internalError("cannot find cross-chain token by erc777 when update currency");
		}
		
		CrossChainToken newToken = maybeToken.get();
		if (newToken.equals(token)) {
			return false;
		}
		
		// update token address in database if changed
		if (!newToken.getAddress().equalsIgnoreCase(token.getAddress())) {
			Currency newCurrency = new Currency();
			BeanUtils.copyProperties(currency, newCurrency);
			newCurrency.setTokenAddress(newToken.getAddress());
			this.dao.updateCurrency(newCurrency);
		}
		
		this.tokens.put(currency.getId(), newToken);
		logger.info("cross-chain token updated: {}", newToken);
		
		return false;
	}
	
	@Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
	public void reloadSafe() {
		try {
			this.reload();
		} catch (RpcException e) {
			if (logger.isDebugEnabled()) {
				logger.debug("Failed to auto refresh cross-chain tokens, " + e.getMessage(), e);
			}
		} catch (Exception e) {
			logger.warn("Failed to auto refresh cross-chain tokens, " + e.getMessage(), e);
		}
	}

}
