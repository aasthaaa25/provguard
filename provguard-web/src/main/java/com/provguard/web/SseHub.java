package com.provguard.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Fans new log events out to Server-Sent Events clients. It only forwards what {@link EventLogIndex} already
 * parsed; it does not score or classify anything. A client whose queue fills up is dropped and reconnects,
 * resuming from its last event id.
 */
public final class SseHub implements EventLogIndex.Listener {
    public static final int MAX_CLIENTS = 16;

    public record Message(String event, long seq, String data) {
    }

    public static final class Client {
        final BlockingQueue<Message> queue = new LinkedBlockingQueue<>(1000);
        volatile boolean overflowed;

        public BlockingQueue<Message> queue() {
            return queue;
        }

        public boolean overflowed() {
            return overflowed;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<Client> clients = ConcurrentHashMap.newKeySet();

    /** Returns null when the client limit is reached. */
    public Client register() {
        if (clients.size() >= MAX_CLIENTS) {
            return null;
        }
        Client client = new Client();
        clients.add(client);
        return client;
    }

    public void unregister(Client client) {
        clients.remove(client);
    }

    public int clientCount() {
        return clients.size();
    }

    @Override
    public void onEvent(EventSummary summary) {
        String data = json(ApiJson.summary(summary));
        Message message = new Message("security-event", summary.seq(), data);
        for (Client client : clients) {
            if (!client.queue.offer(message)) {
                client.overflowed = true;
            }
        }
    }

    @Override
    public void onReset(long epoch) {
        Message message = new Message("reset", -1, json(Map.of("epoch", epoch)));
        for (Client client : clients) {
            client.queue.clear();
            if (!client.queue.offer(message)) {
                client.overflowed = true;
            }
        }
    }

    public String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }
}
