package conflux.dex.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

public class MessageSenderTest {
    private MessageSender sender;
    private TopicResponse PING;
    private TopicResponse data;

    private MessageSender sender() throws Exception{
        Session session = EasyMock.createMock(Session.class);
        RemoteEndpoint.Basic basic = EasyMock.createMock(RemoteEndpoint.Basic.class);
        ByteBuffer pingBuf = ByteBuffer.allocate(Long.BYTES);
        PING = new TopicResponse();
        data = new TopicResponse();

        session.close();
        EasyMock.expectLastCall();
        EasyMock.expect(session.getId()).andReturn("id1").anyTimes();
        EasyMock.expect(session.getBasicRemote()).andReturn(basic).anyTimes();
        basic.sendPing(pingBuf);
        EasyMock.expectLastCall().anyTimes();
        basic.sendText(EasyMock.anyString());
        EasyMock.expectLastCall().anyTimes();
        EasyMock.replay(session);
        EasyMock.replay(basic);

        ExecutorService executor = EasyMock.createMock(ExecutorService.class);
        ObjectMapper mapper = new ObjectMapper();
        return new MessageSender(executor, session, mapper);
    }

    @Test
    public void testGetter() throws Exception{
        sender = sender();
        Assert.assertEquals("id1", this.sender.getId());
//        this.sender.getConnectedTime();
//        this.sender.getSubscribedTopics();
    }

    @Test
    public void testSendMessage() throws Exception {
        sender = sender();
        data.setData(PING);
        data.setTopic("AccountTopic");
        sender.doWork(data);
        sender.doWork(PING);
    }

    @Test
    public void testConsume() throws Exception{
        sender = sender();
        sender.consume(data);
    }

    @Test
    public void testPing() throws Exception{
        sender = sender();
        sender.ping();
        sender.close();
    }

    @Test
    public void testClose() throws Exception{
        sender = sender();
        sender.close();
    }


}
