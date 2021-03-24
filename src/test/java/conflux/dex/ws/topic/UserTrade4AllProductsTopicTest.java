package conflux.dex.ws.topic;

import com.fasterxml.jackson.databind.ObjectMapper;
import conflux.dex.dao.TestDexDao;
import conflux.dex.matching.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.Product;
import conflux.dex.model.Trade;
import conflux.dex.service.EngineService;
import conflux.dex.worker.TradeDetails;
import conflux.dex.ws.TopicRequest;
import org.easymock.EasyMock;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class UserTrade4AllProductsTopicTest {
    private final UserTrade4AllProductsTopic topic = topic();
    private TestDexDao dao;

    private UserTrade4AllProductsTopic topic() {
        dao = new TestDexDao();
        UserTradeTopic userTradeTopic = new UserTradeTopic(dao.get());
        return new UserTrade4AllProductsTopic(dao.get(), userTradeTopic);
    }

    @Test
    public void testRegister() {
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
        Order makerOrder = Order.limitSell(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        Order takerOrder = Order.limitBuy (2, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        Trade trade = new Trade(dao.product.getId(), 2, 1, BigDecimal.valueOf(1), BigDecimal.valueOf(10), OrderSide.Buy, BigDecimal.ZERO, BigDecimal.ZERO);
        TradeDetails tradeDetails = new TradeDetails(trade, takerOrder, makerOrder);

        topic.handle(tradeDetails);
    }

    @Test
    public void testGetIndexModelNotSpecified() throws Exception{
        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);

        topic.getIndex(request);
    }



}
