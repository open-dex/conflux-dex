package dev;

import com.google.gson.Gson;
import conflux.dex.common.Utils;
import conflux.dex.ws.TopicRequest;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WSClient {
	
	private static class Devops {

		public static void log(String format, Object... args) {
	        System.out.print(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()));
	        System.out.print(" ");
	        System.out.printf(format, args);
	        System.out.println();
	    }
		
	}
	
    public static void main(String[] args) throws Exception{
        String url = "";
        url = "ws://localhost:8081/ws";
        List<String> users = Arrays.asList(
                ""
                );
        //
        StandardWebSocketClient client = new StandardWebSocketClient();
        client.doHandshake(new TextWebSocketHandler(){
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                Devops.log("afterConnectionClosed");
            }

            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                Devops.log("afterConnectionEstablished");
                users.stream().filter(u->!u.isEmpty()).forEach(u->{
                    TopicRequest tr = new TopicRequest();
                    tr.setSub(true);
                    tr.setTopic("order.*");
                    HashMap<String, Object> map = new HashMap<String, Object>(1);
                    map.put("address", u);
                    tr.setArguments(map);
                    WebSocketMessage<?> msg = new TextMessage(Utils.toJson(tr));
                    try {
                        session.sendMessage(msg);
                        Devops.log("sub for %s", u);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
            Gson gs = new Gson();
            Object preId = 0;
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                String payload = message.getPayload();
                Map<?, ?> msg = gs.fromJson(payload, Map.class);
                Map<?, ?> data = (Map<?, ?>) msg.get("data");
                Object id = data.get("id");
                if (!Objects.equals(id, preId)) {
                    preId = id;
                    Devops.log("");
                }
                Devops.log("id %s status %10s, this trade: %6s * %20s, total fill: amount %20s funds %20s, " +
                                "product id %2s, completed %5s, topic %6s, eventType %s",
                        id, data.get("status"), data.get("tradePrice"), data.get("tradeAmount"),
                        data.get("filledAmount"),data.get("filledFunds"),
                        data.get("productId"), data.get("completed"), msg.get("topic"),
                        data.get("eventType"));
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                Devops.log("handleTransportError %s", exception);
                exception.printStackTrace(System.out);
            }
        }, url);
/*

*/
        System.out.println("go!");
        System.in.read();

    }
}
