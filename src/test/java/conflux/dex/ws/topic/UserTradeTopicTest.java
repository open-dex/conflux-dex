package conflux.dex.ws.topic;

import com.fasterxml.jackson.databind.ObjectMapper;
import conflux.dex.dao.TestDexDao;
import conflux.dex.matching.Order;
import conflux.dex.model.*;
import conflux.dex.service.EngineService;
import conflux.dex.worker.TradeDetails;
import conflux.dex.ws.TopicRequest;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class UserTradeTopicTest {
    private final UserTradeTopic topic = topic();
    private TestDexDao dao;

    private UserTradeTopic topic() {
        dao = new TestDexDao();
        return new UserTradeTopic(dao.get());
    }

    @Test
    public void testRegister(){
        List<Product> products = new ArrayList<>();
        products.add(dao.product);
        EngineService service = EasyMock.createMock(EngineService.class);
        EasyMock.expect(service.getPreloadedProducts()).andReturn(products);
        EasyMock.replay(service);
        topic.setEngineService(service);

        topic.register(dao.product);

    }

    @Test
    public void testGetIndex() throws Exception{
        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);
        Assert.assertNull(topic.getIndex(request));
    }

    @Test
    public void testHandle() {
        TestDexDao dao = new TestDexDao();
        Order makerOrder = Order.limitSell(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        Order takerOrder = Order.limitBuy (2, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        Trade trade = new Trade(dao.product.getId(), 2, 1, BigDecimal.valueOf(1), BigDecimal.valueOf(10), OrderSide.Buy, BigDecimal.ZERO, BigDecimal.ZERO);
        TradeDetails tradeDetails = new TradeDetails(trade, takerOrder, makerOrder);

        topic.handle(tradeDetails);
    }

}
