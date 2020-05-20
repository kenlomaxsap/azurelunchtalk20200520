package com.example.pixels.eventhubbing;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.pixels.WebSocketExchange;

@Component
public class EventHubProcessor extends Thread {
	
	@Autowired
	IEventHubEndpoint eventhub;
	
	@Autowired
	WebSocketExchange wse;
	
	static int roundCounter = -1;
	
	static private SortedMap<String, Integer> scores = new ConcurrentSkipListMap<String, Integer>();
	static private ConcurrentHashMap<String, TreeSet<Integer>> inside = new ConcurrentHashMap<String, TreeSet<Integer>>();
	static private ConcurrentHashMap<String, TreeSet<Integer>> outside = new ConcurrentHashMap<String, TreeSet<Integer>>();
	static long startTime = Instant.now().toEpochMilli() / 1000;

	private void postScore() {	
		List<Map.Entry<String, Integer>> list = new LinkedList(scores.entrySet());
		list.sort( (me1, me2)-> me2.getValue()-me1.getValue());			
		wse.postScore( list.toString());
	}

	@Scheduled(fixedRate = 100)
	public void collectInsAndOuts() throws Exception {
		Iterable<String> events = eventhub.read();
		for (String msg : events) {
			try {
				JSONObject obj = new JSONObject(msg);
				int x = obj.getInt("x");
				int y = obj.getInt("y");
				int rad = obj.getInt("r");
				int n = obj.getInt("n");
				int w = obj.getInt("w");
				int round = obj.getInt("round");			
				long t = obj.optLong("now", 0);
				String p = obj.getString("patriot");
				long l = t - startTime;
				
				if (t > startTime) {
					int k = x * 10000 + y;
					double dist =  Math.pow(x-w,2 ) + Math.pow(y-n,2);
					if (dist < rad*rad) {
						if (!inside.containsKey(p)) 
							inside.put(p, new TreeSet<Integer>());
						if (!inside.get(p).contains(k)) {
							inside.get(p).add(k);
							scores.put(p, scores.getOrDefault(p, 0) + 1);
						}
					} else {
						if (!outside.containsKey(p)) 
							outside.put(p, new TreeSet<Integer>());	
						if (!outside.get(p).contains(k)) {
							outside.get(p).add(k);
							scores.put(p, scores.getOrDefault(p, 0) - 1);
						}
					}
				}
				if (round>roundCounter) {
					roundCounter = round;
					if (inside.containsKey(p)) 
						inside.get(p).clear();
					if (outside.containsKey(p)) 
						outside.get(p).clear();
				}
				postScore();
			} catch (JSONException e) {
				e.printStackTrace();
				System.out.println(msg);

			}
		}
	}
}
