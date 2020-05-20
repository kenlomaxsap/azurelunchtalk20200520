package com.example.pixels;

import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.pixels.eventhubbing.IEventHubEndpoint;


@Component
@EnableWebSocket
@EnableScheduling
public class WebSocketExchange extends TextWebSocketHandler implements WebSocketConfigurer{

	//@Autowired
	//IEventHubEndpoint eventhub;
	private String thisUrl = null;
	private int websocketCount = 0;
	final int intervalMS=5000;
	private Map<String, Socket> uuids_sockets = new ConcurrentHashMap<String, Socket>();
	private Map<String, List<String>> uuid_replies = new ConcurrentHashMap<String, List<String>>();

	@Override
	public void afterConnectionEstablished(WebSocketSession wss) {
		if (thisUrl==null)
			thisUrl =  "http://"+wss.getUri().getAuthority();
		String uuid = wss.getUri().toString().split("=")[1];		
		
		uuids_sockets.put(uuid, new Socket(wss, uuid, websocketCount++));		
		uuid_replies.put(uuid, new Vector<String>());	
		uuid_replies.get(uuid).add(  toJson("type", "userCount", "data", "" + websocketCount));
	}	

	@Override
	protected void handleTextMessage(WebSocketSession wss, TextMessage msg) throws Exception {	
		outputTray( "ALL",  msg.getPayload());
		//eventhub.send(msg.getPayload());
	}
	
	
	@Scheduled(fixedRate = 100)
	public void tx() {
		for(String k: uuid_replies.keySet()) {
			try{
				if (uuid_replies.get(k).size() > 0) {			
					List<String> msgs = uuid_replies.replace(k, new Vector<String>());
					uuids_sockets.get(k).session.sendMessage(new TextMessage(msgs.toString()));
				}
			} catch (Exception e) {
				System.out.println(e.getMessage());
				uuids_sockets.remove(k);
			}
		}
	}
	
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        registry.addHandler(this, "/sockets");
    }  

	class Socket {
		WebSocketSession session;
		String uuid;
		
		public Socket(WebSocketSession session, String uuid, int count) {
			this.session = session;
			this.uuid = uuid;
		}
	}
	
	//@Scheduled(fixedRate = intervalMS)
	public void newTarget() {
		int edge = 200;
		int rad = 5 + (int)(15*Math.random());
		int west = (int) ((edge - rad / 2) * Math.random() + rad / 2) + 1;
		int north = (int) ((edge - rad / 2) * Math.random() + rad / 2) + 1;
		String msg = toJson("type", "newtarget", "west", "" + west, "north", "" + north, "radius", "" + rad);
		outputTray( "ALL", msg);
	}
	
	public void outputTray(String id, String msg) {
		if (id.equals("ALL")) {
			for (String k: uuids_sockets.keySet()) {
				uuid_replies.get(k).add(msg);
			}
		}
		else 
			uuid_replies.get(id).add(msg);		
	}
	
	public String toJson(String... kvs) {
		JSONObject obj = new JSONObject();
		for (int i = 0; i < kvs.length;) {
			obj.put(kvs[i++], kvs[i++]);
		}
		return obj.toString();
	}


	public void results(String msg) {
		outputTray( "ALL",  toJson("type", "report", "data", msg));
	}
	
	public void postScore(String msg) {
		if (thisUrl==null)
			return;
		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> request = new HttpEntity<String>(msg, headers);	
		restTemplate.postForObject(thisUrl+"/results", request, String.class);
	}

}