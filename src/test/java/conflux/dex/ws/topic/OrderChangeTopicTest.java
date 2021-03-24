package conflux.dex.ws.topic;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import conflux.dex.common.channel.Channel;
import conflux.dex.dao.DexDao;
import conflux.dex.dao.TestDexDao;
import conflux.dex.dao.TestOrderDao;
import conflux.dex.event.OrderEventArg;
import conflux.dex.model.Order;
import conflux.dex.model.OrderSide;
import conflux.dex.model.Trade;
import conflux.dex.service.EngineService;
import conflux.dex.worker.TradeDetails;
import conflux.dex.ws.Subscriber;
import conflux.dex.ws.TopicRequest;
import conflux.dex.ws.TopicResponse;

public class OrderChangeTopicTest {
    private OrderChangeTopic topic;
    private final TestDexDao dao = new TestDexDao();
    private final TestOrderDao orderDao = new TestOrderDao();
    private Order order;

    public OrderChangeTopic topic(DexDao dao) {
        order = new Order();
        return new OrderChangeTopic(dao);
    }

    @Test
    public void testHandle() {
        topic = topic(dao.get());
        order.setProductId(1);
        topic.handle(new OrderEventArg(order, "dummy"));
    }

    @Test
    public void testPublish() {
        topic = topic(dao.get());
        order.setProductId(1);
        order.setUserId(1L);
        UserTopic.Index index = new UserTopic.Index("topic2");
        Subscriber subscriber = new Subscriber() {
            @Override
            public String getId() { return "id1"; }

            @Override
            public long getConnectedTime() { return 0; }

            @Override
            public Set<String> getSubscribedTopics() {
                Set<String> topics = new HashSet<>();
                topics.add("topic1");
                return topics;
            }

            @Override
            public void consume(TopicResponse data) { }

            @Override
            public void ping() { }

            @Override
            public void close() { }
        };
        index.subscribe(subscriber, 1L);
        index.subscribe(subscriber, 2L);
        index.unsubscribe(subscriber, 2L);
        Assert.assertTrue(index.isSubscribed(1L));
        Assert.assertFalse(index.isSubscribed(2L));
        topic.publish(index, order);
    }

    @Test
    public void testRegister() {
        topic = topic(dao.get());
        Channel<Object> channel = Channel.create();
        ExecutorService executor = Executors.newCachedThreadPool();
        EngineService engineService = new EngineService(dao.get(), channel, executor);
        engineService.start();
        engineService.addEngine(dao.product3);

        topic.setEngineService(engineService);
        topic.register(dao.product3);
    }

    @Test
    public void testGetIndex() throws Exception{
        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);

        topic = topic(dao.get());
        Assert.assertNull(topic.getIndex(request));
    }

    @Test
    public void testOnOrderStatusChange() {
        UserTopic.Index index = new UserTopic.Index("topic1");
        Subscriber subscriber = new Subscriber() {
            @Override
            public String getId() { return "1"; }

            @Override
            public long getConnectedTime() { return 0; }

            @Override
            public Set<String> getSubscribedTopics() {
                Set<String> topics = new HashSet<>();
                topics.add("topic1");
                return topics;
            }

            @Override
            public void consume(TopicResponse data) { }

            @Override
            public void ping() { }

            @Override
            public void close() { }
        };
        index.subscribe(subscriber, 1L);
        conflux.dex.matching.Order order = conflux.dex.matching.Order.limitBuy(1, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        order.setUserId(1L);
        topic = topic(orderDao.get());
        topic.onOrderStatusChanged(index, order);

        conflux.dex.matching.Order makerOrder = conflux.dex.matching.Order.limitSell(2, BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        Trade trade = new Trade(dao.product.getId(), 1, 2, BigDecimal.valueOf(1), BigDecimal.valueOf(10), OrderSide.Buy, BigDecimal.ZERO, BigDecimal.ZERO);
        TradeDetails tradeDetails = new TradeDetails(trade, order, makerOrder);

        topic.onOrderMatched(index, tradeDetails);
    }
}
