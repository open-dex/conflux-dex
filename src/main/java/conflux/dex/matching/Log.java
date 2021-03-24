package conflux.dex.matching;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Order matching log, including order matched, opened and cancelled.
 */
public class Log {

	@Override
	public String toString() {
		return "Log [id=" + id + ", productId=" + productId + ", type=" + type + ", takerOrder=" + takerOrder
				+ ", makerOrder=" + makerOrder + ", matchAmount=" + matchAmount + "]";
	}

	private static AtomicLong globalId = new AtomicLong();
	
	private long id;
	private int productId;
	private LogType type;
	private Order takerOrder;
	private Order makerOrder;
	private BigDecimal matchAmount;
	
	public Log() {
		this.id = globalId.getAndIncrement();
	}
	
	private static Log newLog(int productId, LogType type, Order takerOrder, Order makerOrder, BigDecimal amount) {
		Log log = new Log();
		log.setProductId(productId);
		log.setType(type);
		log.setTakerOrder(takerOrder);
		log.setMakerOrder(makerOrder);
		log.setMatchAmount(amount);
		return log;
	}
	
	public static Log newMatchLog(int productId, Order takerOrder, Order makerOrder, BigDecimal amount) {
		return newLog(productId, LogType.OrderMatched, takerOrder, makerOrder, amount);
	}
	
	public static Log newCompleteLog(int productId, Order order, boolean isTaker) {
		if (isTaker) {
			return newLog(productId, LogType.TakerOrderCompleted, order, null, null);
		} else {
			return newLog(productId, LogType.MakerOrderCompleted, null, order, null);
		}
	}
	
	public static Log newOpenLog(int productId, Order takerOrder) {
		return newLog(productId, LogType.TakerOrderOpened, takerOrder, null, null);
	}
	
	public static Log newCancelLog(int productId, Order order, boolean isTaker, boolean byAdmin) {
		if (isTaker) {
			return newLog(productId, LogType.TakerOrderCancelled, order, null, null);
		} else if (byAdmin) {
			return newLog(productId, LogType.MakerOrderCancelledByAdmin, null, order, null);
		} else {
			return newLog(productId, LogType.MakerOrderCancelled, null, order, null);
		}
	}
	
	public static Log newPendingLog(int productId, Order takerOrder, Order makerOrder) {
		return newLog(productId, LogType.OrderPended, takerOrder, makerOrder, null);
	}
	
	public static Log newPendingCancelLog(int productId, Order order) {
		return newLog(productId, LogType.PendingOrderCancelled, null, order, null);
	}
	
	public static Log newOrderBookStatusChangedLog(int productId) {
		return newLog(productId, LogType.OrderBookStatusChanged, null, null, null);
	}
	
	public static Log newOrderBookInitializedLog(int productId) {
		return newLog(productId, LogType.OrderBookInitialized, null, null, null);
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public int getProductId() {
		return productId;
	}
	
	public void setProductId(int productId) {
		this.productId = productId;
	}
	
	public LogType getType() {
		return type;
	}
	
	public void setType(LogType type) {
		this.type = type;
	}
	
	public Order getTakerOrder() {
		return takerOrder;
	}
	
	public void setTakerOrder(Order takerOrder) {
		this.takerOrder = takerOrder;
	}
	
	public Order getMakerOrder() {
		return makerOrder;
	}
	
	public void setMakerOrder(Order makerOrder) {
		this.makerOrder = makerOrder;
	}
	
	public BigDecimal getMatchAmount() {
		return matchAmount;
	}
	
	public void setMatchAmount(BigDecimal matchAmount) {
		this.matchAmount = matchAmount;
	}
}
