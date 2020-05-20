package com.example.pixels.eventhubbing;


import java.util.LinkedList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class EventHubEndpointStub implements IEventHubEndpoint  {
	 int p = 0;
	 LinkedList<String>q = new LinkedList<String>();
	
	public void send(String msg) {
		synchronized (this) {
			q.add(msg);
		}
	}
	
	public List<String> read() {
		synchronized (this) {
			List<String>copy = (List<String>)q.clone(); 
			q.clear();
			return copy;	
		}
	}	
}
