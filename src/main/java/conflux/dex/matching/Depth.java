package conflux.dex.matching;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import conflux.dex.common.Metrics;
import conflux.dex.common.Metrics.QueueMetric;
import conflux.dex.model.OrderSide;

/**
 * Order queue that sorted by price and id (timestamp).
 * For "Buy" side, order is sorted by price DESC, id ASC.
 * For "Sell" side, order is sorted by price ASC, id ASC.
 * Note, concurrency is unnecessary, since order is handled in sequence.
 */
class Depth {
	private OrderSide side;
	private HashMap<Long, Order> orders;
	private TreeSet<Order> queue;
	
	private static final QueueMetric queueMetric = Metrics.queue(Depth.class);
	
	public Depth(OrderSide side) {
		this.side = side;
		this.orders = new HashMap<Long, Order>();
		switch (side) {
		case Buy:
			this.queue = new TreeSet<Order>(new Order.BuyComparator());
			break;
		case Sell:
			this.queue = new TreeSet<Order>(new Order.SellComparator());
			break;
		default:
			throw new IllegalArgumentException("unsupported order side: " + side);
		}
	}
	
	public boolean contains(long orderId) {
		return this.orders.containsKey(orderId);
	}
	
	public boolean add(Order order) {
		if (order.getSide() != this.side) {
			throw new IllegalArgumentException("order side mismatch");
		}
		
		if (this.orders.putIfAbsent(order.getId(), order) != null) {
			return false;
		}
		
		this.queue.add(order);
		
		queueMetric.enqueue();
		
		return true;
	}
	
	public Optional<Order> remove(long orderId) {
		Order order = this.orders.remove(orderId);
		if (order == null) {
			return Optional.empty();
		}
		
		this.queue.remove(order);
		
		queueMetric.dequeue();
		
		return Optional.of(order);
	}
	
	public boolean isEmpty() {
		return this.orders.isEmpty();
	}
	
	public Optional<Order> peek() {
		return this.queue.isEmpty() ? Optional.empty() : Optional.of(this.queue.first());
	}
	
	public Optional<Order> poll() {
		Order order = this.queue.pollFirst();
		if (order == null) {
			return Optional.empty();
		}
		
		this.orders.remove(order.getId());
		
		queueMetric.dequeue();
		
		return Optional.of(order);
	}
	
	public List<Order> filter(Predicate<Order> predicate) {
		return this.queue.stream().filter(predicate).collect(Collectors.toList());
	}
}
