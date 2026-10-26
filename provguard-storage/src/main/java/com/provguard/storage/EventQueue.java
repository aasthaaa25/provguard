package com.provguard.storage;

import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class EventQueue {
    public record PendingEvent(SecurityEvent event, RiskResult fastResult) {
    }

    private final BlockingQueue<PendingEvent> queue;

    public EventQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public boolean offer(PendingEvent event) {
        return queue.offer(event);
    }

    public PendingEvent poll(long timeoutMillis) throws InterruptedException {
        return queue.poll(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public PendingEvent poll() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}
