package conflux.dex.service.statistics;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.influxdb.dto.Point.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.ReportableGauge;
import conflux.dex.event.DepositEventArg;
import conflux.dex.event.OrderEventArg;
import conflux.dex.event.WithdrawEventArg;
import conflux.dex.model.DepositRecord;

@Component
public class TodayActiveUsersStat extends AbstractTodayActiveUsersStat {
	
	private static Logger logger = LoggerFactory.getLogger(TodayActiveUsersStat.class);
	
	private ConcurrentMap<Long, Boolean> total = new ConcurrentHashMap<Long, Boolean>();
	private ConcurrentMap<String, ConcurrentMap<Long, Boolean>> product2TradeUsers = new ConcurrentHashMap<String, ConcurrentMap<Long, Boolean>>();
	private ConcurrentMap<String, ConcurrentMap<Long, Boolean>> currency2DepositUsers = new ConcurrentHashMap<String, ConcurrentMap<Long, Boolean>>();
	private ConcurrentMap<String, ConcurrentMap<Long, Boolean>> currency2WithdrawUsers = new ConcurrentHashMap<String, ConcurrentMap<Long, Boolean>>();

	public TodayActiveUsersStat() {
		Metrics.getOrAdd(new ReportableGauge<Integer>() {

			@Override
			public Integer getValue() {
				tryReset();
				return total.size();
			}

			@Override
			public Builder buildInfluxDBPoint(Builder builder, Integer value) {
				return builder.addField("value", value);
			}
			
		}, TodayActiveUsersStat.class, "total");
		
		this.regGauge("trade", this.product2TradeUsers);
		this.regGauge("deposit", this.currency2DepositUsers);
		this.regGauge("withdraw", this.currency2WithdrawUsers);
	}
	
	private void regGauge(String name, ConcurrentMap<String, ConcurrentMap<Long, Boolean>> stat) {
		Metrics.getOrAdd(new ReportableGauge<Map<String, Integer>>() {

			@Override
			public Map<String, Integer> getValue() {
				tryReset();
				
				Map<String, Integer> result = new HashMap<String, Integer>();
				
				for (Map.Entry<String, ConcurrentMap<Long, Boolean>> entry : stat.entrySet()) {
					result.put(entry.getKey(), entry.getValue().size());
				}
				
				return result;
			}

			@Override
			public Builder buildInfluxDBPoint(Builder builder, Map<String, Integer> value) {
				for (Map.Entry<String, Integer> entry : value.entrySet()) {
					builder.addField(entry.getKey(), entry.getValue());
				}
				
				return builder;
			}
			
		}, TodayActiveUsersStat.class, name);
	}

	@Override
	protected void reset() {
		this.total.clear();
		this.product2TradeUsers.clear();
		this.currency2DepositUsers.clear();
		this.currency2WithdrawUsers.clear();
	}

	@Override
	protected void onOrderEvent(OrderEventArg arg) {
		long userId = arg.order.getUserId();
		total.put(userId, true);
		product2TradeUsers.computeIfAbsent(arg.product, n -> new ConcurrentHashMap<Long, Boolean>()).put(userId, true);
	}

	@Override
	protected void onDeposit(DepositEventArg arg) {
		for (DepositRecord record : arg.records) {
			Long userId = arg.users.get(record.getUserAddress());
			if (userId != null) {
				total.put(userId, true);
				currency2DepositUsers.computeIfAbsent(record.getCurrency(), n -> new ConcurrentHashMap<Long, Boolean>()).put(userId, true);	
			} else {
				logger.error("user id not found by address {}, deposit event arg = {}", record.getUserAddress(), arg);
			}
		}
	}

	@Override
	protected void onWithdraw(WithdrawEventArg arg) {
		long userId = arg.account.getUserId();
		total.put(userId, true);
		currency2WithdrawUsers.computeIfAbsent(arg.record.getCurrency(), n -> new ConcurrentHashMap<Long, Boolean>()).put(userId, true);
	}

}
