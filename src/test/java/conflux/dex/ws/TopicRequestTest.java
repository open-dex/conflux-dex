package conflux.dex.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

public class TopicRequestTest {

    @Test
    public void testGetters() throws Exception{
        String message = "{ \"topic\" : \"topic\", \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);
        Assert.assertNull(request.getArguments());
        Assert.assertEquals("topic", request.getTopic());
    }

    @Test
    public void testSetters() throws Exception{
        String message = "{ \"topic\" : null, \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);
        request.setTopic("new topic");
        request.setSub(false);
        Assert.assertFalse(request.isSub());
        Assert.assertEquals("new topic", request.getTopic());
    }

    @Test
    public void testToString() throws Exception{
        String message = "{ \"topic\" : null, \"sub\" : true, \"arguments\" : null }";
        ObjectMapper mapper = new ObjectMapper();
        TopicRequest request =  mapper.readValue(message, TopicRequest.class);
        Assert.assertEquals(
                String.format("TopicRequest{sub=%s, topic=%s, args=%s}",
                        true, null,  null),
                request.toString()
        );
    }
}
