package conflux.dex.ws.topic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import conflux.dex.common.channel.Channel;
import conflux.dex.dao.TestDexDao;
import conflux.dex.event.OrderEventArg;
import conflux.dex.model.Order;
import conflux.dex.service.EngineService;
import conflux.dex.ws.TopicRequest;

public class OrderChange4AllProductsTopicTest {
    private OrderChange4AllProductsTopic topic;
    private EngineService engineService;

    private TestDexDao dao;
    protected Channel<Object> channel;

    private OrderChange4AllProductsTopic topic() {
        dao = new TestDexDao();
        channel = Channel.create();

        ExecutorService executor = Executors.newCachedThreadPool();
        this.engineService = new EngineService(dao.get(), this.channel, executor);
        this.engineService.start();
        this.engineService.addEngine(dao.product3);

        OrderChangeTopic orderChangeTopic = new OrderChangeTopic(dao.get());
        return new OrderChange4AllProductsTopic(dao.get(), orderChangeTopic);
    }

    @Test
    public void testRegister() {
        topic = topic();
        topic.setEngineService(this.engineService);
        topic.register(dao.product);
    }

    @Test
    public void testGetIndex() throws Exception{
        topic = topic();
        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);

        UserTopic.Index index = new UserTopic.Index("order.*");
        Assert.assertNotSame(index, topic.getIndex(request));
    }

    @Test
    public void testHandle() {
        Order order = new Order();
        topic = topic();
        topic.handle(new OrderEventArg(order, "dummy"));
    }
}
