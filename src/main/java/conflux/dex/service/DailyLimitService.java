package conflux.dex.service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import conflux.dex.common.channel.Sender;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.model.DailyLimit;
import conflux.dex.model.DailyLimitOperation;
import conflux.dex.model.DailyLimitOperationType;
import conflux.dex.model.InstantExchangeProduct;
import conflux.dex.model.Order;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.Product;

@Service
public class DailyLimitService implements AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(DailyLimitService.class);
	
	private static long oneDay = 86400000;
	
	private DexDao dao;
	private Sender<Object> dailyLimitSender;
	private ScheduledExecutorService service;
	private Map<Integer, List<ScheduledFuture<?>>> scheduledTask;
	private ZoneId zoneId = ZoneId.systemDefault();
	
	@Autowired
	public DailyLimitService(DexDao dao, Sender<Object> dailyLimitSender) {
		this.dao = dao;
		this.dailyLimitSender = dailyLimitSender;
		this.service = Executors.newScheduledThreadPool(1);
		((ScheduledThreadPoolExecutor) this.service).setRemoveOnCancelPolicy(true);
		this.scheduledTask = new ConcurrentHashMap<Integer, List<ScheduledFuture<?>>>();
	}
	
	@Autowired
	public void setTimeZone(TimeZone systemTimeZone) {
		this.zoneId = systemTimeZone.toZoneId();
	}
	
	@PostConstruct
	public void setup() {
		logger.info("initialization started ...");
		
		List<Product> products = this.dao.listProducts();
		for (Product product : products) {
			if (product instanceof InstantExchangeProduct) {
				continue;
			}
			this.setupProduct(product.getId());
		}
		
		logger.info("initialization completed");
	}
	
	public void sendDailyLimitOperation(int productId, boolean isOpen) {
		DailyLimitOperation op = isOpen ? DailyLimitOperation.openTrade(productId) : DailyLimitOperation.closeTrade(productId);
		this.dailyLimitSender.send(op);
	}
	
	public void removeScheduledTask(int productId) {
		List<ScheduledFuture<?>> tasks = this.scheduledTask.get(productId);
		if (tasks != null) {
			for (ScheduledFuture<?> task : tasks)
				task.cancel(false);
		}
		this.scheduledTask.put(productId, new ArrayList<ScheduledFuture<?>>());
	}
	
	public void setupProduct(int productId) {
		List<DailyLimit> dailyLimits = this.dao.listDailyLimitsByProductId(productId);
		if (dailyLimits.isEmpty()) {
			return;
		}
			
		removeScheduledTask(productId);
		
		LocalTime now = LocalTime.now(zoneId);
		
		for (DailyLimit dailyLimit : dailyLimits) {
			LocalTime startTime = LocalTime.parse(dailyLimit.getStartTime().toString());
			LocalTime endTime = LocalTime.parse(dailyLimit.getEndTime().toString());
			
			logger.info("setup daily limit, productId = {}, now = {}, startTime = {}, endTime = {}",
					productId, now, startTime, endTime);

			if (now.isAfter(startTime) && now.isBefore(endTime)) {
				this.dailyLimitSender.send(DailyLimitOperation.openTrade(productId));
				logger.info("open trade immediately");
			}
			
			OperationSender openSender = new OperationSender(this.dailyLimitSender, DailyLimitOperation.openTrade(productId), this.dao);
			long initialDelay = Duration.between(now, startTime).toMillis();
			if (startTime.isBefore(now)) {
				initialDelay += oneDay;
			}
			this.scheduledTask.get(productId).add(this.service.scheduleWithFixedDelay(openSender, initialDelay, oneDay, TimeUnit.MILLISECONDS));

			OperationSender closeSender = new OperationSender(this.dailyLimitSender, DailyLimitOperation.closeTrade(productId), this.dao);
			initialDelay = Duration.between(now, endTime).toMillis();
			if (endTime.isBefore(now)) {
				initialDelay += oneDay;
			}
			this.scheduledTask.get(productId).add(this.service.scheduleWithFixedDelay(closeSender, initialDelay, oneDay, TimeUnit.MILLISECONDS));
		}
	}
	
	@PreDestroy
	@Override
	public void close() throws Exception {
		this.service.shutdownNow();
	}
}

class OperationSender implements Runnable {
	
	private static Logger logger = LoggerFactory.getLogger(OperationSender.class);
	
	private Sender<Object> dailyLimitSender;
	private DailyLimitOperation operation;
	private DexDao dao;
	
	public OperationSender(Sender<Object> dailyLimitSender, DailyLimitOperation operation, DexDao dao) {
		this.dailyLimitSender = dailyLimitSender;
		this.operation = operation;
		this.dao = dao;
	}
	
	public void run(){
		if (this.operation.getType() == DailyLimitOperationType.Open) {
			List<Order> pendingOrders = this.dao.listAllOrdersByStatus(this.operation.getProductId(), OrderStatus.Pending);
			Events.PENDING_ORDERS_OPENED.fire(pendingOrders);
		}
		
		this.dailyLimitSender.send(this.operation);
		
		logger.info("change trade status, productId = {}, operation = {}", this.operation.getProductId(), this.operation.getType());
	}
}
