package conflux.dex.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import conflux.dex.model.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.OrderStatus;
import conflux.dex.model.Trade;
import conflux.dex.ws.topic.AccountTopic;

public class InstantExchangeOrderTest extends EngineTester {
	private OrderService service;
	
	@Before
	public void setUp() {
		super.setUp();
		
		this.service = new OrderService(this.dao.get(), this.channel, new AccountTopic(this.dao.get()));
		this.service.init();
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
		BigDecimal aliceOriginalDogAvailable = this.dao.aliceDog.getAvailable();
		BigDecimal bobOriginalCatAvailable = this.dao.bobCat.getAvailable();
		BigDecimal bobOriginalCfxAvailable = this.dao.bobCfx.getAvailable();
		
		// alice sell 100 CAT in CAT-CFX for 1 cfx
		BigDecimal price = BigDecimal.valueOf(0.01);
		PlaceOrderRequest request = PlaceOrderRequest.limitSell(
				this.dao.alice.getName(),
				this.dao.product.getName(),
				price, 
				BigDecimal.valueOf(100));
		Order order = request.toOrder(this.dao.product.getId(), this.dao.alice.getId());
		long aliceOrderId = this.service.placeOrder(order);
		this.waitForOrderStatus(aliceOrderId, OrderStatus.Open);
		
		// bob sell 10 CFX in CFX-DOG for 4000 DOG
		request = PlaceOrderRequest.limitSell(
				this.dao.bob.getName(),
				this.dao.product2.getName(), 
				BigDecimal.valueOf(400),
				BigDecimal.valueOf(10));
		order = request.toOrder(this.dao.product2.getId(), this.dao.bob.getId());
		long bobOrderId = this.service.placeOrder(order);
		this.waitForOrderStatus(bobOrderId, OrderStatus.Open);
		
		// alice use 400 DOG to buy CAT in CAT-DOG
		request = PlaceOrderRequest.marketBuy(
				this.dao.alice.getName(),
				this.dao.product3.getName(),
				BigDecimal.valueOf(400)
				);
		order = request.toOrder(this.dao.product3.getId(), this.dao.alice.getId());
		long orderId = this.service.placeOrder(order);
		this.waitForOrderStatus(orderId, OrderStatus.Filled);

		// check alice order
		Order aliceOrder = this.dao.get().mustGetOrder(aliceOrderId);
		assertEquals(0, aliceOrder.getFilledAmount().compareTo(BigDecimal.valueOf(100)));
		assertEquals(0, aliceOrder.getFilledFunds().compareTo(BigDecimal.valueOf(1)));
		assertEquals(OrderStatus.Filled, aliceOrder.getStatus());
		
		// check bob order
		Order bobOrder = this.dao.get().mustGetOrder(bobOrderId);
		assertEquals(0, bobOrder.getFilledAmount().compareTo(BigDecimal.valueOf(1)));
		assertEquals(0, bobOrder.getFilledFunds().compareTo(BigDecimal.valueOf(400)));
		assertEquals(OrderStatus.Open, bobOrder.getStatus());
		
		// check instant exchange order
		Order ieOrder = this.dao.get().mustGetOrder(orderId);
		assertEquals(0, ieOrder.getFilledAmount().compareTo(BigDecimal.valueOf(100)));
		assertEquals(0, ieOrder.getFilledFunds().compareTo(BigDecimal.valueOf(400)));
		
		// check alice account
		assertEquals(0, this.dao.aliceCat.getHold().compareTo(BigDecimal.valueOf(0)));
		assertEquals(0, this.dao.aliceCat.getAvailable().compareTo(aliceOriginalCatAvailable));
		assertEquals(0, this.dao.aliceCfx.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.aliceCfx.getAvailable().compareTo(aliceOriginalCfxAvailable.add(BigDecimal.valueOf(1))));
		assertEquals(0, this.dao.aliceDog.getHold().compareTo(BigDecimal.valueOf(0)));
		assertEquals(0, this.dao.aliceDog.getAvailable().compareTo(aliceOriginalDogAvailable.subtract(BigDecimal.valueOf(400))));
		
		// check bob account
		assertEquals(0, this.dao.bobCat.getHold().compareTo(BigDecimal.ZERO));
		assertEquals(0, this.dao.bobCat.getAvailable().compareTo(bobOriginalCatAvailable));
		assertEquals(0, this.dao.bobCfx.getHold().compareTo(BigDecimal.valueOf(9)));
		assertEquals(0, this.dao.bobCfx.getAvailable().compareTo(bobOriginalCfxAvailable.subtract(BigDecimal.valueOf(10))));
		assertEquals(0, this.dao.bobDog.getHold().compareTo(BigDecimal.valueOf(0)));
		assertEquals(0, this.dao.bobDog.getAvailable().compareTo(aliceOriginalDogAvailable.add(BigDecimal.valueOf(400))));
		
		// check trade
		List<Trade> result = this.dao.get().listRecentTrades(this.dao.product.getId(), 0, 1000);
		assertEquals(1, result.size());
		Trade trade = result.get(0);
		assertEquals(this.dao.product.getId(), trade.getProductId());
		assertEquals(orderId, trade.getTakerOrderId());
		assertEquals(aliceOrderId, trade.getMakerOrderId());
		assertEquals(0, trade.getPrice().compareTo(price));
		assertEquals(0, trade.getAmount().compareTo(BigDecimal.valueOf(100)));
		assertEquals(OrderSide.Buy, trade.getSide());
		assertNotNull(trade.getCreateTime());
		

		result = this.dao.get().listRecentTrades(this.dao.product2.getId(), 0, 1000);
		assertEquals(1, result.size());
		trade = result.get(0);
		assertEquals(this.dao.product2.getId(), trade.getProductId());
		assertEquals(orderId, trade.getTakerOrderId());
		assertEquals(bobOrderId, trade.getMakerOrderId());
		assertEquals(0, trade.getPrice().compareTo(BigDecimal.valueOf(400)));
		assertEquals(0, trade.getAmount().compareTo(BigDecimal.valueOf(1)));
		assertEquals(OrderSide.Buy, trade.getSide());
		assertNotNull(trade.getCreateTime());
	}
}
