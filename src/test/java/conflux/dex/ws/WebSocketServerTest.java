package conflux.dex.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import conflux.dex.common.BusinessException;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.websocket.Session;
import java.util.concurrent.ExecutorService;

public class WebSocketServerTest {
    private WebSocketServer server;
    private Session session;
    private static final SubscriberManager manager = new SubscriberManager();

    private WebSocketServer webSocketServer() {
        ExecutorService executor = EasyMock.createMock(ExecutorService.class);
        ObjectMapper mapper = new ObjectMapper();

        session = EasyMock.createMock(Session.class);
        EasyMock.expect(session.getId()).andReturn("id1").anyTimes();
        session.setMaxIdleTimeout(EasyMock.anyLong());
        EasyMock.expectLastCall();
        EasyMock.replay(session);

        return new WebSocketServer(manager, executor, mapper);
    }

    @Test(expected = BusinessException.class)
    public void testOpenAndNoTopicRequest() throws Exception{
        this.server = webSocketServer();
        this.server.onOpen(session);

        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        this.server.onMessage(message, session);
    }

    @Test()
    public void testOpenAndClose() {
        this.server = webSocketServer();
        this.server.onOpen(session);
        this.server.onClose(session);
    }

    @Test
    public void testOnError() {
        this.server = webSocketServer();
        this.server.onOpen(session);
        Throwable error = new Error();
        this.server.onError(session, error);
    }

    @Test
    public void testConstructor() {
        this.server = new WebSocketServer();
    }
}
