package conflux.dex.ws;

import org.junit.Assert;
import org.junit.Test;

public class TopicResponseTest {
    private TopicResponse response;

    @Test
    public void testCreate() {
        TopicResponse PING = new TopicResponse();
        response = TopicResponse.create("AccountTopic", PING);
        Assert.assertEquals("AccountTopic", response.getTopic());
    }

    @Test
    public void testTimeStamp() {
        response = new TopicResponse();
        response.setTimestamp(1L);
        Assert.assertEquals(1L, response.getTimestamp());
    }
}
