package com.example.pixels.eventhubbing;

import java.util.List;

public interface IEventHubEndpoint {
	public void send(String msg);
	public List<String> read() ;
}
