package conflux.dex.ws.topic;

import conflux.dex.common.channel.Channel;
import conflux.dex.dao.TestDexDao;
import conflux.dex.service.EngineService;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TickLast24HoursTopicTest {
    private TickLast24HoursTopic topic;
    private final TestDexDao dao = new TestDexDao();
    private final Channel<Object> channel = Channel.create();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Test
    public void testRegister() {
        topic = new TickLast24HoursTopic();
        topic.register(dao.product);
    }

    @Test
    public void testPublishLast24HoursTick() {
        EngineService service = new EngineService(dao.get(), this.channel, this.executor);
        service.start();
        service.addEngine(dao.product);
        topic = new TickLast24HoursTopic();
        topic.setEngineService(service);
        topic.publishLast24HoursTick();
    }
}
