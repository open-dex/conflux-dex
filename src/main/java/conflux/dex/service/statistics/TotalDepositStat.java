package conflux.dex.service.statistics;

import java.math.BigDecimal;
import java.util.HashMap;
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
import conflux.dex.event.DepositEventArg;
import conflux.dex.event.Events;
import conflux.dex.model.Currency;
import conflux.dex.model.DepositRecord;

@Component
public class TotalDepositStat implements GaugeByCurrency, Handler<DepositEventArg> {
	
	private DexDao dao;
	
	private ConcurrentMap<String, BigDecimal> currency2Deposits = new ConcurrentHashMap<String, BigDecimal>();
	private ConcurrentMap<String, String> currency2ConfigKeys = new ConcurrentHashMap<String, String>();
	
	@Autowired
	public TotalDepositStat(DexDao dao) {
		this.dao = dao;
		
		Events.DEPOSIT.addHandler(this);
		
		Metrics.getOrAdd(this, TotalDepositStat.class);
	}

	@Override
	public synchronized void handle(DepositEventArg data) {
		Map<String, BigDecimal> changes = new HashMap<String, BigDecimal>();
		for (DepositRecord record : data.records) {
			BigDecimal current = changes.getOrDefault(record.getCurrency(), BigDecimal.ZERO);
			changes.put(record.getCurrency(), current.add(record.getAmount()));
		}
		
		// already in a database transaction
		for (Map.Entry<String, BigDecimal> entry : changes.entrySet()) {
			BigDecimal newSum = this.currency2Deposits.getOrDefault(entry.getKey(), BigDecimal.ZERO).add(entry.getValue());
			this.currency2Deposits.put(entry.getKey(), newSum);
			
			String configKey = this.getConfigKey(entry.getKey());
			this.dao.setConfig(configKey, newSum.toPlainString());
		}
	}

	@Override
	public Map<String, BigDecimal> getValue() {
		return this.currency2Deposits;
	}
	
	@PostConstruct
	public void init() {
		List<Currency> currencies = this.dao.listCurrencies();
		for (Currency currency : currencies) {
			// try to load from config table
			String configKey = this.getConfigKey(currency.getName());
			Optional<String> configValue = this.dao.getConfig(configKey);
			if (configValue.isPresent()) {
				this.currency2Deposits.put(currency.getName(), new BigDecimal(configValue.get()));
			} else {
				// if config unavailable, load from deposit table and set config table
				BigDecimal sum = this.dao.getDepositSum(currency.getName());
				this.dao.setConfig(configKey, sum.toPlainString());
				this.currency2Deposits.put(currency.getName(), sum);
			}
		}
	}
	
	private String getConfigKey(String currency) {
		return this.currency2ConfigKeys.computeIfAbsent(currency, c -> String.format("stat.deposit.total.%s", c));
	}

}
