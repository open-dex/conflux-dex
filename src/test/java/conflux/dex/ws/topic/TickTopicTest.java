package conflux.dex.ws.topic;

import conflux.dex.dao.TestDexDao;
import conflux.dex.model.Product;
import conflux.dex.model.Tick;
import conflux.dex.service.EngineService;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TickTopicTest {
    @Test
    public void testHandle() {
        TickTopic topic = new TickTopic();
        Tick tick = new Tick();
        tick.setGranularity(1);
        topic.handle(tick);
    }

    @Test
    public void testRegister() {
        TickTopic topic = new TickTopic();
        TestDexDao dao = new TestDexDao();

        List<Product> products = new ArrayList<>();
        products.add(dao.product);
        EngineService service = EasyMock.createMock(EngineService.class);
        EasyMock.expect(service.getPreloadedProducts()).andReturn(products);
        EasyMock.replay(service);

        topic.setEngineService(service);
        topic.register(dao.product);
    }
}
