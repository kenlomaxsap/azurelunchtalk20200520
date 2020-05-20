package com.example.pixels.eventhubbing;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.stereotype.Component;

import com.microsoft.azure.eventhubs.ConnectionStringBuilder;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventHubClient;
import com.microsoft.azure.eventhubs.EventPosition;
import com.microsoft.azure.eventhubs.PartitionReceiver;

//@Component
public class EventHubEndpoint implements IEventHubEndpoint  {

	static EventHubClient ehClient;
	
	static ScheduledExecutorService executorService;

	static AtomicInteger i = new AtomicInteger();

	static int offset = 0;

	@Value("${eventhub.namespace}")
	private String eventhubnamespace;
	@Value("${eventhub.name}")
	private String eventhubname;
	@Value("${eventhub.policyname}")
	private String eventhubpolicyname;
	@Value("${eventhub.key}")
	private String eventhubkey;

	static PartitionReceiver receiver;

	@PostConstruct
	public void postConstruct() throws Exception {
		final ConnectionStringBuilder connStr = new ConnectionStringBuilder().setNamespaceName(eventhubnamespace).setEventHubName(eventhubname).setSasKeyName(eventhubpolicyname).setSasKey(eventhubkey);
		executorService = Executors.newScheduledThreadPool(1);
		ehClient = EventHubClient.createFromConnectionStringSync(connStr.toString(), executorService);
		String partitionId = ehClient.getRuntimeInformation().get().getPartitionIds()[0];
		receiver = ehClient.createEpochReceiverSync(EventHubClient.DEFAULT_CONSUMER_GROUP_NAME, partitionId, EventPosition.fromStartOfStream(), 1);
	}

	public List<String> read() {
		List<String> l = new ArrayList<String>();
		try {
				for (EventData ev: receiver.receiveSync(250)) {
					if (ev.getBytes() != null)
						l.add(new String(ev.getBytes(), Charset.defaultCharset()));
				}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return l;
	}

	public void finalize() throws Exception {
		receiver.close().thenComposeAsync(aVoid -> ehClient.close(), executorService).whenCompleteAsync((t, u) -> {
				if (u != null) {
					System.out.println(String.format("closing failed with error: %s", u.toString()));
				}
			}, executorService).get();

		ehClient.closeSync();
		executorService.shutdown();
	}

	private static class MessageAndResult {
		public final int messageNumber;
		public final EventData message;
		public CompletableFuture<Void> result;

		public MessageAndResult(final int messageNumber, final EventData message) {
			this.messageNumber = messageNumber;
			this.message = message;
		}
	}

	public void send(String msg) {
		MessageAndResult mar = new MessageAndResult(i.getAndIncrement(),
				EventData.create(msg.getBytes(Charset.defaultCharset())));
		mar.result = ehClient.send(mar.message);
	}

	@Bean
	public static PropertySourcesPlaceholderConfigurer propertyConfigInDev() {
		return new PropertySourcesPlaceholderConfigurer();
	}

}