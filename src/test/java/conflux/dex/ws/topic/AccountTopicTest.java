package conflux.dex.ws.topic;

import com.fasterxml.jackson.databind.ObjectMapper;
import conflux.dex.common.BusinessException;
import conflux.dex.dao.TestDexDao;
import conflux.dex.model.Product;
import conflux.dex.service.EngineService;
import conflux.dex.ws.Subscriber;
import conflux.dex.ws.TopicRequest;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AccountTopicTest {
    private final AccountTopic topic = topic();
    private TestDexDao dao;

    private AccountTopic topic() {
        dao = new TestDexDao();
        return new AccountTopic(dao.get());
    }

    @Test(expected = NullPointerException.class)
    public void testRegister() {
        List<Product> products = new ArrayList<>();
        products.add(dao.product);
        EngineService service = EasyMock.createMock(EngineService.class);
        EasyMock.expect(service.getPreloadedProducts()).andReturn(products);
        EasyMock.replay(service);
        topic.setEngineService(service);
        topic.register(dao.product);
    }

    @Test(expected = BusinessException.class)
    public void testGetIndexModelNotSpecified() throws Exception{
        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);

        topic.getIndex(request);
    }

    @Test(expected = BusinessException.class)
    public void testGetIndexModel() {
        Object model = EasyMock.createMock(Object.class);
        HashMap<String, Object> arguments = new HashMap<>();
        arguments.put("model", model);
        TopicRequest request = EasyMock.createMock(TopicRequest.class);
        EasyMock.expect(request.getArguments()).andReturn(arguments).anyTimes();
        EasyMock.replay(request, model);

        topic.getIndex(request);
    }

    @Test(expected = BusinessException.class)
    public void testSubscribe() throws Exception{
        Subscriber subscriber = EasyMock.createMock(Subscriber.class);
        EasyMock.expect(subscriber.getId()).andReturn("id1");

        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);

        topic.subscribe(subscriber, request);
    }

    @Test(expected = BusinessException.class)
    public void testUnsubscribe() throws Exception{
        Subscriber subscriber = EasyMock.createMock(Subscriber.class);
        EasyMock.expect(subscriber.getId()).andReturn("id1");

        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);

        topic.unsubscribe(subscriber, request);
    }
}
