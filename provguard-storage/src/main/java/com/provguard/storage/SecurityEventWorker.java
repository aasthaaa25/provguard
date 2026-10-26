package com.provguard.storage;

import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Slow path. Detailed scoring and JSONL writing stay off the target application thread.
 */
public final class SecurityEventWorker implements Runnable, AutoCloseable {
    private final EventQueue queue;
    private final SlowPathEnricher enricher;
    private final List<SecurityEventObserver> observers;
    private final EventStatistics statistics;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService executor;
    private volatile Thread worker;

    public SecurityEventWorker(
            EventQueue queue,
            SlowPathEnricher enricher,
            List<SecurityEventObserver> observers,
            EventStatistics statistics) {
        this.queue = queue;
        this.enricher = enricher;
        this.observers = List.copyOf(observers);
        this.statistics = statistics;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "provguard-event-worker");
            thread.setDaemon(true);
            worker = thread;
            return thread;
        });
    }

    public void start() {
        executor.submit(this);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                EventQueue.PendingEvent pending = queue.poll(200);
                if (pending != null) {
                    dispatch(pending);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (!running.get()) {
                    return;
                }
            } catch (Throwable failure) {
                System.err.println("ProvGuard background worker recovered from an error. " + failure.getMessage());
            }
        }
    }

    public void dispatch(EventQueue.PendingEvent pending) {
        RiskResult result;
        try {
            result = enricher.enrich(pending.event(), pending.fastResult());
        } catch (Throwable failure) {
            result = pending.fastResult();
            System.err.println("ProvGuard enrichment failed. Logging the fast-path result. " + failure.getMessage());
        }
        for (SecurityEventObserver observer : observers) {
            try {
                observer.onEvent(pending.event(), result);
            } catch (Throwable failure) {
                System.err.println("ProvGuard observer failed. Target execution continues. " + failure.getMessage());
            }
        }
        statistics.reviseModel(pending.fastResult(), result);
        statistics.onLogged();
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        EventQueue.PendingEvent pending;
        while ((pending = queue.poll()) != null) {
            dispatch(pending);
        }
    }

    public interface SlowPathEnricher {
        RiskResult enrich(SecurityEvent event, RiskResult fastResult);
    }
}
