package conflux.dex.ws.topic;

import conflux.dex.common.channel.Channel;
import conflux.dex.dao.TestDexDao;
import conflux.dex.service.EngineService;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DepthTopicTest {

    @Test
    public void testRegister() {
        DepthTopic topic = new DepthTopic();
        TestDexDao dao = new TestDexDao();

        topic.register(dao.product);
    }

    @Test
    public void testPublish() {
        TestDexDao dao = new TestDexDao();
        Channel<Object> channel = Channel.create();
        ExecutorService executor = Executors.newCachedThreadPool();

        EngineService engineService = new EngineService(dao.get(), channel, executor);
        engineService.start();
        engineService.addEngine(dao.product3);
        engineService.addEngine(dao.product);

        DepthTopic topic = new DepthTopic();
        topic.setEngineService(engineService);
        topic.publish();
    }
}
