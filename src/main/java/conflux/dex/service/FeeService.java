package conflux.dex.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import conflux.dex.model.User;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import conflux.dex.common.BusinessException;
import conflux.dex.dao.DexDao;
import conflux.dex.model.Currency;
import conflux.dex.model.FeeData;
import conflux.dex.model.FeeStrategy;
import conflux.dex.model.Product;

@Service
public class FeeService {
	
	private static final String KEY_TAKER_FEE_RATE = "fee.rate.taker";
	private static final String KEY_MAKER_FEE_RATE = "fee.rate.maker";
	private static final String KEY_FEE_STRATEGY = "fee.strategy";
	private static final String KEY_FEE_RECIPIENT = "fee.recipient";
	
	private DexDao dao;
	private AccountService accountService;
	
	private AtomicReference<DataWrapper> wrapper = new AtomicReference<DataWrapper>(new DataWrapper(new FeeData()));
	
	@Autowired
	public FeeService(DexDao dao, AccountService accountService) {
		this.dao = dao;
		this.accountService = accountService;
		
		this.reload();
	}

	public FeeData getData() {
		return this.wrapper.get().data;
	}
	
	public long[] getDexFeeRecipientAccountIds(int productId) {
		DataWrapper wrapper = this.wrapper.get();
		
		if (wrapper.data.getStrategy() != FeeStrategy.FeeToDex) {
			return null;
		}
		
		return wrapper.dexAccountIdCache.computeIfAbsent(productId, pid -> {
			Product product = this.dao.getProduct(pid).mustGet();
			Currency baseCurrency = this.dao.getCurrency(product.getBaseCurrencyId()).mustGet();
			Currency quoteCurrency = this.dao.getCurrency(product.getQuoteCurrencyId()).mustGet();
			User user = AccountService.getOrAddUser(dao, wrapper.data.getFeeRecipient());
			return new long[] {
					this.accountService.getOrAddAccount(user.getId(), baseCurrency.getName(), BigDecimal.ZERO).getId(),
					this.accountService.getOrAddAccount(user.getId(), quoteCurrency.getName(), BigDecimal.ZERO).getId(),
			};
		});
	}
	
	@Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
	public void reload() {
		FeeData copy = new FeeData();
		FeeData current = this.wrapper.get().data;
		
		BeanUtils.copyProperties(current, copy);
		
		Optional<String> value;
		
		value = this.dao.getConfig(KEY_TAKER_FEE_RATE);
		if (value.isPresent()) {
			copy.setTakerFeeRate(new BigDecimal(value.get()));
		}
		
		value = this.dao.getConfig(KEY_MAKER_FEE_RATE);
		if (value.isPresent()) {
			copy.setMakerFeeRate(new BigDecimal(value.get()));
		}
		
		value = this.dao.getConfig(KEY_FEE_STRATEGY);
		if (value.isPresent()) {
			copy.setStrategy(FeeStrategy.valueOf(value.get()));
		}
		
		value = this.dao.getConfig(KEY_FEE_RECIPIENT);
		if (value.isPresent()) {
			copy.setFeeRecipient(value.get());
		}
		
		if (copy.getStrategy() == FeeStrategy.FeeToDex && StringUtils.isEmpty(copy.getFeeRecipient())) {
			throw BusinessException.internalError("fee recipient not configured");
		}
		
		if (!current.equals(copy)) {
			this.wrapper.set(new DataWrapper(copy));
		}
	}
	
	private static class DataWrapper {
		public FeeData data;
		public Map<Integer, long[]> dexAccountIdCache = new ConcurrentHashMap<Integer, long[]>();
		
		public DataWrapper(FeeData data) {
			this.data = data;
		}
	}

}
