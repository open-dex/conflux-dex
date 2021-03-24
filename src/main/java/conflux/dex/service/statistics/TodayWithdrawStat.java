package conflux.dex.service.statistics;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

import conflux.dex.event.Events;
import conflux.dex.event.WithdrawEventArg;

@Component
public class TodayWithdrawStat extends TodayDataStat<WithdrawEventArg, Map<String, BigDecimal>> implements GaugeByCurrency {
	
	private ConcurrentMap<String, BigDecimal> currency2Withdraws = new ConcurrentHashMap<String, BigDecimal>();

	public TodayWithdrawStat() {
		super(Events.WITHDRAW_COMPLETED);
	}

	@Override
	protected synchronized void update(WithdrawEventArg data) {
		BigDecimal current = this.currency2Withdraws.getOrDefault(data.record.getCurrency(), BigDecimal.ZERO);
		this.currency2Withdraws.put(data.record.getCurrency(), current.add(data.record.getAmount()));
	}

	@Override
	protected void reset() {
		this.currency2Withdraws.clear();
	}

	@Override
	protected Map<String, BigDecimal> get() {
		return this.currency2Withdraws;
	}

}
