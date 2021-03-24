package conflux.dex.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import conflux.dex.model.Account;
import conflux.dex.model.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.Trade;
import conflux.dex.model.User;
import conflux.dex.ws.topic.AccountTopic;

public class OrderServiceTest extends EngineTester {
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
		BigDecimal aliceOriginalCatAvailable = this.dao.aliceCat.getAvailable();
		BigDecimal aliceOriginalCfxAvailable = this.dao.aliceCfx.getAvailable();
		BigDecimal bobOriginalCatAvailable = this.dao.bobCat.getAvailable();
		BigDecimal bobOriginalCfxAvailable = this.dao.bobCfx.getAvailable();
		
		// alice sell 100 CAT
		BigDecimal price = BigDecimal.valueOf(0.01);
		PlaceOrderRequest request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product.getName(),
				price, 
				BigDecimal.valueOf(100));
		Order order = request.toOrder(this.dao.product.getId(), this.dao.alice.getId());
		long aliceOrderId = this.service.placeOrder(order);
		this.waitForOrderStatus(aliceOrderId, OrderStatus.Open);
		
		// bob buy 20 CAT, so only 20 filled
		request = PlaceOrderRequest.limitBuy(
				this.dao.bob.getName(),
				this.dao.product.getName(), 
				BigDecimal.valueOf(0.02), // use higher price to test refund
				BigDecimal.valueOf(20));
		order = request.toOrder(this.dao.product.getId(), this.dao.bob.getId());
		long bobOrderId = this.service.placeOrder(order);
		this.waitForOrderStatus(bobOrderId, OrderStatus.Filled);
		
		// check alice order
		Order aliceOrder = this.dao.get().mustGetOrder(aliceOrderId);
		assertEquals(0, aliceOrder.getFilledAmount().compareTo(BigDecimal.valueOf(20)));
		assertEquals(0, aliceOrder.getFilledFunds().compareTo(BigDecimal.valueOf(0.2)));
		
		// check bob order
		Order bobOrder = this.dao.get().mustGetOrder(bobOrderId);
		assertEquals(0, bobOrder.getFilledAmount().compareTo(BigDecimal.valueOf(20)));
		assertEquals(0, bobOrder.getFilledFunds().compareTo(BigDecimal.valueOf(0.2)));
		
		// check alice account
		assertEquals(0, this.dao.aliceCat.getHold().compareTo(BigDecimal.valueOf(80)));
		assertEquals(0, this.dao.aliceCat.getAvailable().compareTo(aliceOriginalCatAvailable.subtract(BigDecimal.valueOf(100))));
		assertEquals(0, this.dao.aliceCfx.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.aliceCfx.getAvailable().compareTo(aliceOriginalCfxAvailable.add(BigDecimal.valueOf(0.2))));
		
		// check bob account
		assertEquals(0, this.dao.bobCat.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.bobCat.getAvailable().compareTo(bobOriginalCatAvailable.add(BigDecimal.valueOf(20))));
		assertEquals(0, this.dao.bobCfx.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.bobCfx.getAvailable().compareTo(bobOriginalCfxAvailable.subtract(BigDecimal.valueOf(0.2))));
		
		// check trade
		List<Trade> result = this.dao.get().listRecentTrades(this.dao.product.getId(), 0, 1000);
		assertEquals(1, result.size());
		Trade trade = result.get(0);
		assertEquals(this.dao.product.getId(), trade.getProductId());
		assertEquals(bobOrderId, trade.getTakerOrderId());
		assertEquals(aliceOrderId, trade.getMakerOrderId());
		assertEquals(0, trade.getPrice().compareTo(price));
		assertEquals(0, trade.getAmount().compareTo(BigDecimal.valueOf(20)));
		assertEquals(OrderSide.Buy, trade.getSide());
		assertNotNull(trade.getCreateTime());
	}
	
	@Test(timeout = 1000)
	public void testCancelAll() throws Exception {
		BigDecimal originalAvailable = this.dao.aliceCat.getAvailable();
		PlaceOrderRequest request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product.getName(),
				BigDecimal.valueOf(0.01), 
				BigDecimal.valueOf(100));
		Order order = request.toOrder(this.dao.product.getId(), this.dao.alice.getId());
		long orderId = this.service.placeOrder(order);
		this.waitForOrderStatus(orderId, OrderStatus.Open);
		
		// succeed to cancel
		this.service.cancelOrder(orderId);
		this.waitForOrderStatus(orderId, OrderStatus.Cancelled);
		
		// balance not changed
		order = this.dao.get().mustGetOrder(orderId);
		assertEquals(0, order.getFilledAmount().compareTo(BigDecimal.ZERO));
		assertEquals(0, order.getFilledFunds().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.aliceCat.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.aliceCat.getAvailable().compareTo(originalAvailable));
	}
	
	@Test(timeout = 1000)
	public void testCancelPartial() throws Exception {
		BigDecimal bobOriginalCatAvailable = this.dao.bobCat.getAvailable();
		BigDecimal bobOriginalCfxAvailable = this.dao.bobCfx.getAvailable();
		
		// alice sell 100 CAT
		BigDecimal price = BigDecimal.valueOf(0.01);
		PlaceOrderRequest request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product.getName(), 
				price, 
				BigDecimal.valueOf(100));
		Order order = request.toOrder(this.dao.product.getId(), this.dao.alice.getId());
		long orderId = this.service.placeOrder(order);
		this.waitForOrderStatus(orderId, OrderStatus.Open);
		
		// bob buy 200 CAT, so 100 CAT filled from alice
		request = PlaceOrderRequest.limitBuy(
				this.dao.bob.getName(), 
				this.dao.product.getName(), 
				price, 
				BigDecimal.valueOf(200));
		order = request.toOrder(this.dao.product.getId(), this.dao.bob.getId());
		orderId = this.service.placeOrder(order);
		this.waitForOrderStatus(orderId, OrderStatus.Open);
		
		// cancel partial filled order of bob
		service.cancelOrder(orderId);
		this.waitForOrderStatus(orderId, OrderStatus.Cancelled);
		
		// filled amount is 100 in order
		order = this.dao.get().mustGetOrder(orderId);
		assertEquals(0, order.getAmount().compareTo(BigDecimal.valueOf(200)));
		assertEquals(0, order.getFilledAmount().compareTo(BigDecimal.valueOf(100)));
		assertEquals(0, order.getFilledFunds().compareTo(BigDecimal.valueOf(1)));
		
		// check trade
		List<Trade> result = this.dao.get().listTradesByOrderId(orderId, 0, 1);
		assertEquals(1, result.size());
		assertEquals(0, result.get(0).getAmount().compareTo(BigDecimal.valueOf(100)));
		
		// check bob account
		// 1. CAT account: +100
		// 2. CFX account: -100*0.01 = -1
		assertEquals(0, this.dao.bobCat.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.bobCat.getAvailable().compareTo(bobOriginalCatAvailable.add(BigDecimal.valueOf(100))));
		assertEquals(0, this.dao.bobCfx.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.bobCfx.getAvailable().compareTo(bobOriginalCfxAvailable.subtract(BigDecimal.valueOf(1))));
	}

	@Test(timeout = 1000)
	public void testCancelOrders() throws Exception {
		BigDecimal originalAvailable = this.dao.aliceCat.getAvailable();
		// alice sell 100 CAT
		PlaceOrderRequest request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product.getName(),
				BigDecimal.valueOf(0.01),
				BigDecimal.valueOf(100));
		Order order = request.toOrder(this.dao.product.getId(), this.dao.alice.getId());
		long orderId = this.service.placeOrder(order);
		this.waitForOrderStatus(orderId, OrderStatus.Open);

		// alice sell 20 cfx
		request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product4.getName(),
				BigDecimal.valueOf(0.01),
				BigDecimal.valueOf(20));
		order = request.toOrder(this.dao.product.getId(), this.dao.alice.getId());
		long orderId2 = this.service.placeOrder(order);
		this.waitForOrderStatus(orderId2, OrderStatus.Open);

		this.service.cancelOrders(this.dao.alice.getId(), this.dao.cfx.getId());
		this.waitForOrderStatus(orderId, OrderStatus.Cancelled);
		this.waitForOrderStatus(orderId2, OrderStatus.Cancelled);

		// balance not changed
		Order aliceOrder = this.dao.get().mustGetOrder(orderId);
		Order aliceOrder2 = this.dao.get().mustGetOrder(orderId2);
		assertEquals(0, aliceOrder.getFilledAmount().compareTo(BigDecimal.ZERO));
		assertEquals(0, aliceOrder.getFilledFunds().compareTo(BigDecimal.ZERO));
		assertEquals(0, aliceOrder2.getFilledAmount().compareTo(BigDecimal.ZERO));
		assertEquals(0, aliceOrder2.getFilledFunds().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.aliceCat.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.aliceCat.getAvailable().compareTo(originalAvailable));
	}

	@Test(timeout = 1000)
	public void testTradeFee() throws Exception {
		String feeOwner1 = "DEX FEE OWNER - 1";
		String feeOwner2 = "DEX FEE OWNER - 2";
		
		BigDecimal aliceOriginalCatAvailable = this.dao.aliceCat.getAvailable();
		BigDecimal aliceOriginalCfxAvailable = this.dao.aliceCfx.getAvailable();
		BigDecimal bobOriginalCatAvailable = this.dao.bobCat.getAvailable();
		BigDecimal bobOriginalCfxAvailable = this.dao.bobCfx.getAvailable();
		
		// alice sell 100 CAT
		BigDecimal price = BigDecimal.valueOf(0.01);
		PlaceOrderRequest request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product.getName(),
				price, 
				BigDecimal.valueOf(100));
		request.setFeeAddress(feeOwner1);
		request.setFeeRateTaker(0.01);
		request.setFeeRateMaker(0.02);
		Order order = request.toOrder(this.dao.product.getId(), this.dao.alice.getId());
		long aliceOrderId = this.service.placeOrder(order);
		this.waitForOrderStatus(aliceOrderId, OrderStatus.Open);
		
		// bob buy 20 CAT, so only 20 filled
		request = PlaceOrderRequest.limitBuy(
				this.dao.bob.getName(),
				this.dao.product.getName(), 
				price,
				BigDecimal.valueOf(20));
		request.setFeeAddress(feeOwner2);
		request.setFeeRateTaker(0.03);
		request.setFeeRateMaker(0.04);
		order = request.toOrder(this.dao.product.getId(), this.dao.bob.getId());
		long bobOrderId = this.service.placeOrder(order);
		this.waitForOrderStatus(bobOrderId, OrderStatus.Filled);
		
		// check alice order
		Order aliceOrder = this.dao.get().mustGetOrder(aliceOrderId);
		assertEquals(0, aliceOrder.getFilledAmount().compareTo(BigDecimal.valueOf(20)));
		assertEquals(0, aliceOrder.getFilledFunds().compareTo(BigDecimal.valueOf(0.2)));
		
		// check bob order
		Order bobOrder = this.dao.get().mustGetOrder(bobOrderId);
		assertEquals(0, bobOrder.getFilledAmount().compareTo(BigDecimal.valueOf(20)));
		assertEquals(0, bobOrder.getFilledFunds().compareTo(BigDecimal.valueOf(0.2)));
		
		// check alice account: trade funds = 0.2, makerFee = 0.2 * 2% = 0.004
		assertEquals(0, this.dao.aliceCat.getHold().compareTo(BigDecimal.valueOf(80)));
		assertEquals(0, this.dao.aliceCat.getAvailable().compareTo(aliceOriginalCatAvailable.subtract(BigDecimal.valueOf(100))));
		assertEquals(0, this.dao.aliceCfx.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.aliceCfx.getAvailable().compareTo(aliceOriginalCfxAvailable.add(BigDecimal.valueOf(0.196))));
		User feeUser1 = this.dao.get().getUserByName(feeOwner1).mustGet();
		Account feeAccount1 = AccountService.mustGetAccount(dao.get(), feeUser1.getId(), this.dao.cfx.getName());
		assertEquals(0, feeAccount1.getAvailable().compareTo(BigDecimal.valueOf(0.004)));
		
		// check bob account: trade amount = 20, takerFee = 20 * 3% = 0.6
		assertEquals(0, this.dao.bobCat.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.bobCat.getAvailable().compareTo(bobOriginalCatAvailable.add(BigDecimal.valueOf(19.4))));
		User feeUser2 = this.dao.get().getUserByName(feeOwner2).mustGet();
		Account feeAccount2 = AccountService.mustGetAccount(dao.get(), feeUser2.getId(), this.dao.cat.getName());
		assertEquals(0, feeAccount2.getAvailable().compareTo(BigDecimal.valueOf(0.6)));
		assertEquals(0, this.dao.bobCfx.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.bobCfx.getAvailable().compareTo(bobOriginalCfxAvailable.subtract(BigDecimal.valueOf(0.2))));
		
		// check trade
		List<Trade> result = this.dao.get().listRecentTrades(this.dao.product.getId(), 0, 1000);
		assertEquals(1, result.size());
		Trade trade = result.get(0);
		assertEquals(this.dao.product.getId(), trade.getProductId());
		assertEquals(bobOrderId, trade.getTakerOrderId());
		assertEquals(aliceOrderId, trade.getMakerOrderId());
		assertEquals(0, trade.getPrice().compareTo(price));
		assertEquals(0, trade.getAmount().compareTo(BigDecimal.valueOf(20)));
		assertEquals(OrderSide.Buy, trade.getSide());
		assertNotNull(trade.getCreateTime());
		// TODO check for fee settlement
	}

}
