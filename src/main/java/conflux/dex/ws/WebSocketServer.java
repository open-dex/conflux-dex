package conflux.dex.ws;

import java.util.concurrent.ExecutorService;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@ServerEndpoint("/ws")
@Component
public class WebSocketServer {
	
	private static final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);
	private static SubscriberManager manager;
	private static ExecutorService executor;
	private static ObjectMapper mapper;
	
	public WebSocketServer() {
	}
	
	@Autowired
	public WebSocketServer(SubscriberManager manager, ExecutorService executor, ObjectMapper mapper) {
		WebSocketServer.manager = manager;
		WebSocketServer.executor = executor;
		WebSocketServer.mapper = mapper;
	}
	
	@OnOpen
	public void onOpen(Session session) {
		session.setMaxIdleTimeout(SubscriberManager.SessionTimeoutMillis);
		
		manager.add(new MessageSender(executor, session, mapper));
	}
	
	@OnClose
	public void onClose(Session session) {
		manager.remove(session.getId());
	}
	
	@OnMessage
	public void onMessage(String message, Session session) throws Exception {
		TopicRequest request = mapper.readValue(message, TopicRequest.class);
		manager.onTopicRequest(session.getId(), request);
	}
	
	@OnError
	public void onError(Session session, Throwable error) {
		logger.debug("failed to handle WebSocket message", error);
	}

}
