package conflux.dex.service.blockchain;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import conflux.dex.blockchain.TypedOrder;
import conflux.dex.dao.ArchiveDao;
import conflux.dex.model.User;
import conflux.dex.service.OrderService;
import conflux.dex.tool.SpringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;

import conflux.dex.blockchain.crypto.Domain;
import conflux.dex.common.Handler;
import conflux.dex.common.Metrics;
import conflux.dex.config.BlockchainPruneConfig;
import conflux.dex.dao.DexDao;
import conflux.dex.event.Events;
import conflux.dex.matching.Log;
import conflux.dex.matching.LogType;
import conflux.dex.matching.Order;
import conflux.dex.matching.PruneLog;
import conflux.dex.model.OrderPruneRecord;
import conflux.dex.service.blockchain.settle.Settleable;
import conflux.dex.service.blockchain.settle.prune.PruneOrderSettlement;
import conflux.dex.service.blockchain.settle.prune.PruneSettlement;
import conflux.dex.tool.contract.BoomflowContract;
import conflux.web3j.Cfx;

/**
 * PruneOrderService is used to prune historical orders in Boomflow contract to avoid 
 * a large amount of collateral for storage.
 * 
 * However, we cannot delete all completed orders on chain, because Boomflow contract
 * need to prevent replay attack from DEX admin.
 * 
 * Current solution is as below:
 * 1. Record a timestamp in Boomflow contract, and only accept new orders that created
 * after the timestamp. On the other hand, DEX admin could only delete completed orders
 * that created before the timestamp on chain. In this way, Boomflow contract can easily
 * prevent replay attack based on timestamp and recent completed orders.
 * 2. However, there are some orders that opened and never matched for a long time. Boomflow
 * contract should accept such orders even the order timestamp is before the value in contract.
 * So, before DEX admin update the timestamp on chain, the unmatched orders should be recorded
 * on chain as well.
 */
@Service
public class PruneOrderService implements Handler<Log> {
	
	private static Logger logger = LoggerFactory.getLogger(PruneOrderService.class);
	
	private static Histogram statRecorded = Metrics.histogram(PruneOrderService.class, "recorded");
	private static Histogram statPruned = Metrics.histogram(PruneOrderService.class, "pruned");
	private static Meter statCancelled = Metrics.meter(PruneOrderService.class, "cancelled");
	
	private DexDao dao;
	private ArchiveDao archiveDao;
	private OrderService orderService;
	private BlockchainSettlementService service;
	
	private Set<Integer> handledIndices = new HashSet<Integer>();
	private List<Order> orders = new LinkedList<Order>();
	
	private AtomicLong lastExpiration = new AtomicLong();
	
	@Autowired
	private BlockchainPruneConfig config = new BlockchainPruneConfig();
	
	private NavigableSet<OrderPruneRecord> completedOrders = new ConcurrentSkipListSet<OrderPruneRecord>();
	
	@Autowired
	public PruneOrderService(DexDao dao, BlockchainSettlementService service) {
		this.dao = dao;
		this.service = service;
		
		Events.ORDER_CANCELLED.addHandler(order -> onOrderCancelled(order));
		Events.ORDER_FILLED.addHandler(order -> onOrderFilled(order));
		Events.PENDING_ORDERS_OPENED.addHandler(orders -> {
			recordOrders(orders);
		});
		
		Metrics.dump(this);
	}

	@Autowired
	public void setDependency(ArchiveDao dao, OrderService orderService) {
		this.archiveDao = dao;
		this.orderService = orderService;
	}
	
	public long getLastExpiration() {
		return this.lastExpiration.get();
	}
	
	public int getCompletedOrderCount() {
		return this.completedOrders.size();
	}

	private final AtomicBoolean initialized = new AtomicBoolean(false);
	private void init(Cfx cfx) {
		if (initialized.get()) {
			return;
		}
		initialized.set(true);
		logger.info("init start.");
		int offset = 0;
		int limit = 1000;
		long now = System.currentTimeMillis();

		List<OrderPruneRecord> records;
		String boomflowAddress = Domain.boomflow().verifyingContract;
		logger.info("boom address {}", boomflowAddress);
		BoomflowContract bfc = new BoomflowContract(cfx, boomflowAddress);


		LinkedList<OrderPruneRecord> toBeDeleted = new LinkedList<>();
		do {
			records = this.dao.getOrderPruneRecords(now, offset, limit);
			if (records.isEmpty()) {
				break;
			}
			logger.info("check order online , size {}", records.size());
			// in case of pruned on chain already.
			for (OrderPruneRecord record : records) {
				BoomflowContract.Order order = bfc.getOrder(record.getHash());
				if (order.timestamp == 0) {
					//already deleted.
					logger.info("Order not found on chain, id {} hash {}", record.getOrderId(), record.getHash());
					toBeDeleted.add(record);
				} else {
					this.completedOrders.add(record);
				}
			}
			
			offset += records.size();
		} while (records.size() >= limit);
		// delete orders from database once pruned on chain
		toBeDeleted.forEach(record->{
			this.dao.deleteOrderPruneRecord(record.getTimestamp(), record.getOrderId());
		});
		
		// prune immediately according to the timestamp on chain
		this.lastExpiration.set(bfc.getTimestamp());
		logger.info("init done.");
	}
	
	private void onOrderFilled(Order order) {
		String orderHash = dao.mustGetOrder(order.getId()).getHash();
		this.completedOrders.add(OrderPruneRecord.create(order.getTimestamp(), order.getId(), orderHash));
	}
	
	private void onOrderCancelled(Order order) {
		// cancel never matched orders if already recorded on chain.
		if (!order.isEverMatched() && order.getTimestamp() < this.lastExpiration.get()) {
			this.service.submit(Settleable.orderCancelled(order.getId(), null, 0));
			statCancelled.mark();
		}
		
		// add prune record if order matched or recorded on chain.
		if (order.isEverMatched() || order.getTimestamp() < this.lastExpiration.get()) {
			String orderHash = dao.mustGetOrder(order.getId()).getHash();
			this.completedOrders.add(OrderPruneRecord.create(order.getTimestamp(), order.getId(), orderHash));
		}
	}

	@Override
	public synchronized void handle(Log data) {
		if (data.getType() != LogType.OrderPruned) {
			return;
		}
		
		PruneLog log = (PruneLog) data;
		int logProductIndex = log.getRequest().getProductIndex();
		
		if (this.handledIndices.add(logProductIndex)) {
			this.orders.addAll(log.getOrders());
		} else {
			// If failed to handle log (e.g. db operation failed when updateExpiration), 
			// it will be handled automatically. In this case, do nothing.
			logger.debug("handle prune log again, request = {}", log.getRequest());
		}
		
		// continue to receive rest orders of other products
		if (this.handledIndices.size() < log.getRequest().getTotalProducts()) {
			return;
		}
		
		this.updateExpiration(log.getRequest().getEndTimeExclusive());
		
		statRecorded.update(this.orders.size());
		this.lastExpiration.set(log.getRequest().getEndTimeExclusive());
		this.handledIndices.clear();
		this.orders.clear();
	}
	
	private void updateExpiration(long timestamp) {
		// upload pending orders that never matched
		if (!this.orders.isEmpty()) {
			List<Long> orderIds = this.orders.stream().map(order -> order.getId()).collect(Collectors.toList());
			List<conflux.dex.model.Order> orders = this.archiveDao.listOrderByIds(orderIds);
			this.recordOrders(orders);
		}

		// update expiration
		this.service.submit(PruneSettlement.updateBoomflowTimestamp(this.config.updateTimestampGasLimit, timestamp));
	}

	public void recordOrders(List<conflux.dex.model.Order> orders) {
		List<TypedOrder> orderHashes = new ArrayList<TypedOrder>(this.config.uploadOrderBatchSize);
		List<Long> userIds = orders.stream().map(order -> order.getUserId()).collect(Collectors.toList());
		Map<Long,User> uidMap = this.dao.getUsers(userIds).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));
		for (conflux.dex.model.Order order : orders) {
			TypedOrder typedOrder = this.orderService.modelOrder2typed(order, uidMap.get(order.getUserId()).getName());
			orderHashes.add(typedOrder);
			
			if (orderHashes.size() >= this.config.uploadOrderBatchSize) {
				BigInteger gasLimit = this.config.batchUploadOrdersGasLimit(orderHashes.size());
				this.service.submit(PruneOrderSettlement.upload(orderHashes, gasLimit));
				orderHashes = new ArrayList<>(this.config.uploadOrderBatchSize);
			}
		}
		
		if (!orderHashes.isEmpty()) {
			BigInteger gasLimit = this.config.batchUploadOrdersGasLimit(orderHashes.size());
			this.service.submit(PruneOrderSettlement.upload(orderHashes, gasLimit));
		}
	}
	public void recordOrdersByIds(List<Long> ids) {
		List<conflux.dex.model.Order> orders =
				this.archiveDao.listOrderByIds(ids);
		this.recordOrders(orders);
	}
	@Scheduled(
			initialDelayString = "${blockchain.prune.order.interval.delete.millis:60000}",
			fixedDelayString = "${blockchain.prune.order.interval.delete.millis:60000}")
	public void prune() {
		init(SpringTool.getBean(Cfx.class));
		logger.debug("prune order start.");
		List<OrderPruneRecord> records = new ArrayList<OrderPruneRecord>(this.config.deleteBatchSize);
		long upperBound = this.lastExpiration.get();
		int pruned = 0;
		
		while (!this.completedOrders.isEmpty() && this.completedOrders.first().getTimestamp() < upperBound) {
			records.add(this.completedOrders.pollFirst());
			pruned++;
			
			if (records.size() >= this.config.deleteBatchSize) {
				BigInteger gasLimit = this.config.batchDeleteOrdersGasLimit(records.size());
				this.service.submit(PruneOrderSettlement.delete(records, gasLimit));
				records = new ArrayList<OrderPruneRecord>(this.config.deleteBatchSize);
			}
		}
		
		if (!records.isEmpty()) {
			BigInteger gasLimit = this.config.batchDeleteOrdersGasLimit(records.size());
			this.service.submit(PruneOrderSettlement.delete(records, gasLimit));
		}
		
		statPruned.update(pruned);
		logger.debug("prune order done.");
	}

}
