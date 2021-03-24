package conflux.dex.ws.topic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.easymock.EasyMock;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import conflux.dex.common.channel.Channel;
import conflux.dex.dao.TestDexDao;
import conflux.dex.model.BestBidOffer;
import conflux.dex.service.EngineService;
import conflux.dex.ws.Subscriber;
import conflux.dex.ws.TopicRequest;

public class BboTopicTest {
    @Test
    public void testHandle() {
        BboTopic topic = new BboTopic();
        TestDexDao dao = new TestDexDao();
        BestBidOffer offer = new BestBidOffer();
        offer.setProduct(dao.product.getName());
        topic.handle(offer);
    }

    @Test
    public void testRegister() {
        TestDexDao dao = new TestDexDao();
        Channel<Object> channel = Channel.create();
        ExecutorService executor = Executors.newCachedThreadPool();

        EngineService engineService = new EngineService(dao.get(), channel, executor);
        engineService.start();
        engineService.addEngine(dao.product3);

        BboTopic topic = new BboTopic();
        topic.setEngineService(engineService);
        topic.register(dao.product3);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribe() throws Exception{
        Subscriber subscriber = EasyMock.createMock(Subscriber.class);
        EasyMock.expect(subscriber.getId()).andReturn("id1");

        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);

        BboTopic topic = new BboTopic();
        topic.subscribe(subscriber, request);
    }

    @Test
    public void testUnsubscribe() throws Exception{
        Subscriber subscriber = EasyMock.createMock(Subscriber.class);
        EasyMock.expect(subscriber.getId()).andReturn("id1");

        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);

        BboTopic topic = new BboTopic();
        Object data = new Object();
        topic.publish("topic", data);
        topic.unsubscribe(subscriber, request);
    }
}
