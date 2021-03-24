package conflux.dex.service.statistics;

import java.time.Duration;
import java.time.Instant;

import com.codahale.metrics.Gauge;

import conflux.dex.common.Event;
import conflux.dex.common.Handler;
import conflux.dex.common.Metrics;
import conflux.dex.event.DepositEventArg;
import conflux.dex.event.Events;
import conflux.dex.event.OrderEventArg;
import conflux.dex.event.WithdrawEventArg;
import conflux.dex.worker.ticker.DefaultTickGranularity;

abstract class AbstractTodayDataStat {
	
	private Instant resetTime;
	
	protected AbstractTodayDataStat() {
		this.resetTime = DefaultTickGranularity.localToday().plus(Duration.ofDays(1));
	}
	
	protected abstract void reset();
	
	protected void tryReset() {
		Instant now = Instant.now();
		
		if (now.isBefore(this.resetTime)) {
			return;
		}
		
		synchronized (this.resetTime) {
			if (now.isBefore(this.resetTime)) {
				return;
			}
			
			this.reset();
			this.resetTime = DefaultTickGranularity.localToday().plus(Duration.ofDays(1));
		}
	}
	
}

abstract class TodayDataStat<T, D> extends AbstractTodayDataStat implements Handler<T>, Gauge<D> {
	
	protected TodayDataStat(Event<T> event) {
		event.addHandler(this);
		
		Metrics.getOrAdd(this, this.getClass());
	}
	
	protected abstract void update(T data);
	protected abstract D get();

	@Override
	public void handle(T data) {
		this.tryReset();
		
		this.update(data);
	}
	
	@Override
	public D getValue() {
		this.tryReset();
		
		return this.get();
	}

}

abstract class AbstractTodayActiveUsersStat extends AbstractTodayDataStat {

	protected AbstractTodayActiveUsersStat() {
		Handler<OrderEventArg> orderEventHandler = data -> {
			tryReset();
			onOrderEvent(data);
		};
		
		Events.PLACE_ORDER_SUBMITTED.addHandler(orderEventHandler);
		Events.CANCEL_ORDER_SUBMITTED.addHandler(orderEventHandler);
		
		Events.DEPOSIT.addHandler(data -> {
			tryReset();
			onDeposit(data);
		});
		
		Events.WITHDRAW_COMPLETED.addHandler(data -> {
			tryReset();
			onWithdraw(data);
		});
	}
	
	protected abstract void onOrderEvent(OrderEventArg arg);
	protected abstract void onDeposit(DepositEventArg arg);
	protected abstract void onWithdraw(WithdrawEventArg arg);
	
}
