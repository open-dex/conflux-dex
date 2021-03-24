package conflux.dex.ws.topic;

import conflux.dex.dao.TestDexDao;
import conflux.dex.matching.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.Product;
import conflux.dex.model.Trade;
import conflux.dex.service.EngineService;
import conflux.dex.worker.TradeDetails;
import org.easymock.EasyMock;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TradeTopicTest {

    @Test
    public void testRegister() {
        TestDexDao dao = new TestDexDao();
        TradeTopic topic = new TradeTopic();

        List<Product> products = new ArrayList<>();
        products.add(dao.product);
        EngineService service = EasyMock.createMock(EngineService.class);
        EasyMock.expect(service.getPreloadedProducts()).andReturn(products);
        EasyMock.replay(service);

        topic.setEngineService(service);
        topic.register(dao.product);
    }

    @Test
    public void testHandle() {
        TestDexDao dao = new TestDexDao();
        TradeTopic topic = new TradeTopic();

        Order makerOrder = Order.limitSell(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        Order takerOrder = Order.limitBuy (2, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        Trade trade = new Trade(dao.product.getId(), 2, 1, BigDecimal.valueOf(1), BigDecimal.valueOf(10), OrderSide.Buy, BigDecimal.ZERO, BigDecimal.ZERO);
        TradeDetails tradeDetails = new TradeDetails(trade, takerOrder, makerOrder);

        topic.handle(tradeDetails);
    }
}
