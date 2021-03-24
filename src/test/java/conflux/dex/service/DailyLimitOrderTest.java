package conflux.dex.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import conflux.dex.common.BusinessException;
import conflux.dex.model.*;
import org.junit.Before;
import org.junit.Test;

import conflux.dex.ws.topic.AccountTopic;

import static org.junit.Assert.*;

public class DailyLimitOrderTest extends EngineTester {
	private OrderService service;

	@Before
	public void setUp() {
		super.setUp();
		
		this.service = new OrderService(this.dao.get(), this.channel, new AccountTopic(this.dao.get()));
	}
	
	private void waitForOrderStatus(long orderId, OrderStatus status) throws InterruptedException {
		while (this.dao.get().mustGetOrder(orderId).getStatus() != status) {
			Thread.sleep(10);
		}
	}
	
	@Test(timeout = 1000)
	public void testPlaceOrder() throws Exception {
//		BigDecimal aliceOriginalCatAvailable = this.dao.aliceCat.getAvailable();
		BigDecimal aliceOriginalCfxAvailable = this.dao.aliceCfx.getAvailable();
//		BigDecimal aliceOriginalDogAvailable = this.dao.aliceDog.getAvailable();
		BigDecimal aliceOriginalBearAvailable = this.dao.aliceBear.getAvailable();
//		BigDecimal bobOriginalCatAvailable = this.dao.bobCat.getAvailable();
		BigDecimal bobOriginalCfxAvailable = this.dao.bobCfx.getAvailable();
//		BigDecimal bobOriginalDogAvailable = this.dao.bobDog.getAvailable();
		BigDecimal bobOriginalBearAvailable = this.dao.bobBear.getAvailable();
		
		
		// alice sell 5 cfx in CFX-BEAR with price 100
		PlaceOrderRequest request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product4.getName(),
				BigDecimal.valueOf(100),
				BigDecimal.valueOf(5));
		// check request
		assertEquals(this.dao.alice.getName(), request.getAddress());
		assertEquals(this.dao.product4.getName(), request.getProduct());
		assertEquals(OrderType.Limit, request.getType());
		assertEquals(OrderSide.Sell, request.getSide());
		assertEquals(BigDecimal.valueOf(100), request.getPrice());
		assertEquals(BigDecimal.valueOf(5), request.getAmount());
		assertNotNull(request.getTimestamp());
		assertNotNull(request.getSignature());
		assertNull(request.getClientOrderId());
		assertNotNull(request.getFeeAddress());

		Order order = request.toOrder(this.dao.product4.getId(), this.dao.alice.getId());
		long aliceOrderId = this.service.placeOrder(order);
		LocalTime now = LocalTime.now(ZoneId.of("Asia/Shanghai"));
		if (now.isBefore(LocalTime.parse("09:00:00")) || now.isAfter(LocalTime.parse("21:00:00"))) {
			waitForOrderStatus(aliceOrderId, OrderStatus.Pending);
			return;
		} else 
			waitForOrderStatus(aliceOrderId, OrderStatus.Open);
		
		// alice sell 3 cfx in CFX-BEAR with price 111 which is higher than upper bound
		request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product4.getName(),
				BigDecimal.valueOf(111),
				BigDecimal.valueOf(3));
		order = request.toOrder(this.dao.product4.getId(), this.dao.alice.getId());
		long aliceOrderId2 = this.service.placeOrder(order);
		waitForOrderStatus(aliceOrderId2, OrderStatus.Open);
		
		// alice sell 2 cfx in CFX-BEAR with price 89 which is lower than lower bound
		request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(), 
				this.dao.product4.getName(),
				BigDecimal.valueOf(89), 
				BigDecimal.valueOf(2));
		order = request.toOrder(this.dao.product4.getId(), this.dao.alice.getId());
		long aliceOrderId3 = this.service.placeOrder(order);
		waitForOrderStatus(aliceOrderId3, OrderStatus.Pending);
		
		// bob buy 10 cfx in CFX-BEAR with price 200
		request = PlaceOrderRequest.limitBuy(
				this.dao.bob.getName(),
				this.dao.product4.getName(),
				BigDecimal.valueOf(200),
				BigDecimal.valueOf(10));
		order = request.toOrder(this.dao.product4.getId(), this.dao.bob.getId());
		long bobOrderId = this.service.placeOrder(order);
		waitForOrderStatus(bobOrderId, OrderStatus.Pending);
		
		// check alice first order
		Order aliceOrder = this.dao.get().mustGetOrder(aliceOrderId);
		assertEquals(0, aliceOrder.getFilledAmount().compareTo(BigDecimal.valueOf(5)));
		assertEquals(0, aliceOrder.getFilledFunds().compareTo(BigDecimal.valueOf(500)));
		
		// check bob order
		Order bobOrder = this.dao.get().mustGetOrder(bobOrderId);
		assertEquals(0, bobOrder.getFilledAmount().compareTo(BigDecimal.valueOf(5)));
		assertEquals(0, bobOrder.getFilledFunds().compareTo(BigDecimal.valueOf(500)));
		
		// check alice account
		assertEquals(0, this.dao.aliceCfx.getHold().compareTo(BigDecimal.valueOf(5)));
		assertEquals(0, this.dao.aliceCfx.getAvailable().compareTo(aliceOriginalCfxAvailable.subtract(BigDecimal.valueOf(10))));
		assertEquals(0, this.dao.aliceBear.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.aliceBear.getAvailable().compareTo(aliceOriginalBearAvailable.add(BigDecimal.valueOf(500))));
		
		// check bob account
		assertEquals(0, this.dao.bobCfx.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.bobCfx.getAvailable().compareTo(bobOriginalCfxAvailable.add(BigDecimal.valueOf(5))));
		assertEquals(0, this.dao.bobBear.getHold().compareTo(BigDecimal.valueOf(1000)));
		assertEquals(0,	this.dao.bobBear.getAvailable().compareTo(bobOriginalBearAvailable.subtract(BigDecimal.valueOf(1500))));
		
		// check trade
		List<Trade> result = this.dao.get().listRecentTrades(this.dao.product4.getId(), 0, 1000);
		assertEquals(1, result.size());
		Trade trade = result.get(0);
		assertEquals(this.dao.product4.getId(), trade.getProductId());
		assertEquals(bobOrderId, trade.getTakerOrderId());
		assertEquals(aliceOrderId, trade.getMakerOrderId());
		assertEquals(0, trade.getPrice().compareTo(BigDecimal.valueOf(100)));
		assertEquals(0, trade.getAmount().compareTo(BigDecimal.valueOf(5)));
		assertEquals(OrderSide.Buy, trade.getSide());
		assertNotNull(trade.getCreateTime());
		
		System.out.println(this.dao.get().getRecentTradeBefore(this.dao.product4.getId(), new Timestamp(999999999999999L)));
		
		this.channel.send(DailyLimitOperation.closeTrade(4));
		request = PlaceOrderRequest.limitBuy(
				this.dao.bob.getName(),
				this.dao.product4.getName(),
				BigDecimal.valueOf(105),
				BigDecimal.valueOf(10));
		order = request.toOrder(this.dao.product4.getId(), this.dao.bob.getId());
		long bobOrderId2 = this.service.placeOrder(order);
		waitForOrderStatus(bobOrderId2, OrderStatus.Pending);
		this.channel.send(DailyLimitOperation.openTrade(4));
		waitForOrderStatus(bobOrderId2, OrderStatus.Open);
		waitForOrderStatus(bobOrderId, OrderStatus.Pending);
		waitForOrderStatus(aliceOrderId2, OrderStatus.Open);
		waitForOrderStatus(aliceOrderId3, OrderStatus.Pending);
	}

	@Test(expected = BusinessException.class)
	public void testValidateInvalidClientOrderId() {
		// limit sell instant exchange product
		PlaceOrderRequest request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product4.getName(),
				BigDecimal.valueOf(100),
				BigDecimal.valueOf(5));
		String str = "sssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssssss";
		request.setClientOrderId(str);
		request.validate(this.dao.product4);
	}

	@Test
	public void testGetPreloadedProducts() {
		assertEquals(4, super.getPreloadedProducts().size());
	}

	@Test
	public void testGetDepth() {
		assertNull(super.getDepth(this.dao.product3.getId(), 1, 1));
	}

	@Test
	public void testGetLast24HoursTick() {
		assertNull(super.getLast24HoursTick(this.dao.product3.getId()));
	}
}
