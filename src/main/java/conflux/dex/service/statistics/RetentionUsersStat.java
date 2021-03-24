package conflux.dex.service.statistics;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.influxdb.dto.Point.Builder;
import org.springframework.stereotype.Component;

import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.ReportableGauge;
import conflux.dex.common.Utils;
import conflux.dex.event.DepositEventArg;
import conflux.dex.event.OrderEventArg;
import conflux.dex.event.WithdrawEventArg;

@Component
public class RetentionUsersStat extends AbstractTodayActiveUsersStat implements ReportableGauge<Map<String, Integer>> {
	
	private RetentionUsers[] rus = {
			new RetentionUsers(1),
			new RetentionUsers(3),
			new RetentionUsers(7),
			new RetentionUsers(14),
			new RetentionUsers(30),
	};
	
	public RetentionUsersStat() {
		Metrics.getOrAdd(this, RetentionUsersStat.class);
	}

	private void update(long userId) {
		for (RetentionUsers rs : this.rus) {
			rs.update(userId);
		}
	}

	@Override
	protected void reset() {
		for (RetentionUsers rs : this.rus) {
			rs.seal();
		}
	}

	@Override
	protected void onOrderEvent(OrderEventArg arg) {
		this.update(arg.order.getUserId());
	}

	@Override
	protected void onDeposit(DepositEventArg arg) {
		for (long userId : arg.users.values()) {
			this.update(userId);
		}
	}

	@Override
	protected void onWithdraw(WithdrawEventArg arg) {
		this.update(arg.account.getUserId());
	}

	@Override
	public Map<String, Integer> getValue() {
		this.tryReset();
		
		Map<String, Integer> result = new HashMap<String, Integer>();
		result.put("day1", this.rus[0].get());
		result.put("day3", this.rus[1].get());
		result.put("day7", this.rus[2].get());
		result.put("day14", this.rus[3].get());
		result.put("day30", this.rus[4].get());
		
		return result;
	}

	@Override
	public Builder buildInfluxDBPoint(Builder builder, Map<String, Integer> value) {
		for (Map.Entry<String, Integer> entry : value.entrySet()) {
			builder.addField(entry.getKey(), entry.getValue());
		}

		return builder;
	}

}

class RetentionUsers {
	
	private RetentionWindow window;
	
	private ConcurrentMap<Long, Boolean> todayActiveUsers = new ConcurrentHashMap<Long, Boolean>();
	private AtomicInteger numRetentioned = new AtomicInteger();

	public RetentionUsers(int retentionDays) {
		this.window = new RetentionWindow(retentionDays);
	}

	public void update(long userId) {
		this.todayActiveUsers.put(userId, true);
		
		boolean retentioned = this.window.removeUser(userId);
		if (retentioned) {
			this.numRetentioned.incrementAndGet();
		}
	}

	public void seal() {
		this.window.move(this.todayActiveUsers);
		this.todayActiveUsers = new ConcurrentHashMap<Long, Boolean>();
		this.numRetentioned = new AtomicInteger();
	}

	public int get() {
		return this.numRetentioned.get();
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
	
}

class RetentionWindow {
	
	private int numSlots;
	private Deque<ConcurrentMap<Long, Boolean>> slots;
	private ConcurrentMap<Long, ConcurrentMap<Long, Boolean>> user2Slots = new ConcurrentHashMap<Long, ConcurrentMap<Long, Boolean>>();
	
	public RetentionWindow(int numSlots) {
		this.numSlots = numSlots;
		this.slots = new ArrayDeque<ConcurrentMap<Long, Boolean>>(numSlots);
	}
	
	public boolean removeUser(long userId) {
		ConcurrentMap<Long, Boolean> slot = this.user2Slots.remove(userId);
		if (slot == null) {
			return false;
		}
		
		slot.remove(userId);
		return true;
	}
	
	public void move(ConcurrentMap<Long, Boolean> newSlot) {
		if (this.slots.size() == this.numSlots) {
			ConcurrentMap<Long, Boolean> slot = this.slots.removeFirst();
			for (Long userId : slot.keySet()) {
				this.user2Slots.remove(userId);
			}
		}
		
		for (Long userId : newSlot.keySet()) {
			this.removeUser(userId);
			this.user2Slots.put(userId, newSlot);
		}
		
		this.slots.addLast(newSlot);
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
	
}