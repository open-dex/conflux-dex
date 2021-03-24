package conflux.dex.service.statistics;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import conflux.dex.event.DepositEventArg;
import conflux.dex.event.Events;
import conflux.dex.model.DepositRecord;

@Component
public class TodayDepositStat extends TodayDataStat<DepositEventArg, Map<String, BigDecimal>> implements GaugeByCurrency {
	
	private ConcurrentMap<String, BigDecimal> currency2Deposits = new ConcurrentHashMap<String, BigDecimal>();

	public TodayDepositStat() {
		super(Events.DEPOSIT);
	}

	@Override
	protected synchronized void update(DepositEventArg data) {
		for (DepositRecord record : data.records) {
			BigDecimal current = this.currency2Deposits.getOrDefault(record.getCurrency(), BigDecimal.ZERO);
			this.currency2Deposits.put(record.getCurrency(), current.add(record.getAmount()));
		}
	}

	@Override
	protected void reset() {
		this.currency2Deposits.clear();
	}

	@Override
	protected Map<String, BigDecimal> get() {
		return this.currency2Deposits;
	}

}
