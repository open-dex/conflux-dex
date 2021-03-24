package conflux.dex.service.statistics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import conflux.dex.common.Handler;
import conflux.dex.common.Metrics;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.event.WithdrawEventArg;
import conflux.dex.model.Currency;
import conflux.dex.model.WithdrawType;

@Component
public class TotalWithdrawStat implements GaugeByCurrency, Handler<WithdrawEventArg> {
	
	protected DexDao dao;
	
	private ConcurrentMap<String, BigDecimal> currency2Withdraws = new ConcurrentHashMap<String, BigDecimal>();
	private ConcurrentMap<String, String> currency2ConfigKeys = new ConcurrentHashMap<String, String>();
	protected String configKeyPrefix = "stat.withdraw.total.";
	
	@Autowired
	public TotalWithdrawStat(DexDao dao) {
		this.dao = dao;
		
		Events.WITHDRAW_COMPLETED.addHandler(this);
		
		Metrics.getOrAdd(this, this.getClass());
	}

	@Override
	public synchronized void handle(WithdrawEventArg data) {
		BigDecimal newSum = this.currency2Withdraws.getOrDefault(data.record.getCurrency(), BigDecimal.ZERO).add(data.record.getAmount());
		this.currency2Withdraws.put(data.record.getCurrency(), newSum);
		
		String configKey = this.getConfigKey(data.record.getCurrency());
		this.dao.setConfig(configKey, newSum.toPlainString());
	}

	@Override
	public Map<String, BigDecimal> getValue() {
		return this.currency2Withdraws;
	}

	@PostConstruct
	public void init() {
		List<Currency> currencies = this.dao.listCurrencies();
		for (Currency currency : currencies) {
			// try to load from config table
			String configKey = this.getConfigKey(currency.getName());
			Optional<String> configValue = this.dao.getConfig(configKey);
			if (configValue.isPresent()) {
				this.currency2Withdraws.put(currency.getName(), new BigDecimal(configValue.get()));
			} else {
				// if config unavailable, load from deposit table and set config table
				BigDecimal sum = this.getWithdrawSum(currency.getName());
				this.dao.setConfig(configKey, sum.toPlainString());
				this.currency2Withdraws.put(currency.getName(), sum);
			}
		}
	}
	
	protected BigDecimal getWithdrawSum(String currency) {
		return this.dao.getWithdrawSum(currency);
	}
	
	private String getConfigKey(String currency) {
		return this.currency2ConfigKeys.computeIfAbsent(currency, c -> this.configKeyPrefix + c);
	}

}

@Component
class TotalForceWithdrawStat extends TotalWithdrawStat {

	@Autowired
	public TotalForceWithdrawStat(DexDao dao) {
		super(dao);
		
		this.configKeyPrefix = "stat.withdraw.force.";
	}
	
	@Override
	public void handle(WithdrawEventArg data) {
		if (data.record.getType() == WithdrawType.OnChainForce) {
			super.handle(data);
		}
	}
	
	@Override
	protected BigDecimal getWithdrawSum(String currency) {
		return this.dao.getWithdrawSumByType(currency, WithdrawType.OnChainForce);
	}
}
