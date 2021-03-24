package conflux.dex.service.statistics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.codahale.metrics.Counter;

import conflux.dex.common.Handler;
import conflux.dex.common.Metrics;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.User;

@Component
public class TotalUsersStat implements Handler<User> {
	
	private Counter totalUsersCounter = Metrics.counter(TotalUsersStat.class);
	
	public TotalUsersStat() {
		Events.NEW_USER_ADDED.addHandler(this);
	}
	
	@Override
	public void handle(User data) {
		this.totalUsersCounter.inc();
	}
	
	@Autowired
	public void init(DexDao dao) {
		long totalUsers = dao.getUserCount();
		this.totalUsersCounter.inc(totalUsers);
	}
	
	public long getValue() {
		return this.totalUsersCounter.getCount();
	}

}
