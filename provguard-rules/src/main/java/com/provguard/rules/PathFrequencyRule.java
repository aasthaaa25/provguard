package com.provguard.rules;

import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts repeated process-execution signatures inside a short time window.
 * Observing a signature is separate from scoring so the fast path records frequency once.
 */
public final class PathFrequencyRule extends AbstractRiskRule {
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int threshold;
    private final long windowMillis;

    public PathFrequencyRule(Clock clock, int threshold, Duration window) {
        super("REPEATED_PROCESS_EXECUTION");
        this.clock = clock;
        this.threshold = threshold;
        this.windowMillis = window.toMillis();
    }

    public int observe(SinkType sinkType, String pathSignature) {
        if (sinkType != SinkType.RUNTIME_EXEC && sinkType != SinkType.PROCESS_BUILDER_START) {
            return 1;
        }
        long now = clock.millis();
        Deque<Long> deque = hits.computeIfAbsent(pathSignature, key -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMillis) {
                deque.removeFirst();
            }
            deque.addLast(now);
            return deque.size();
        }
    }

    public void reset() {
        hits.clear();
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (event.sinkType() != SinkType.RUNTIME_EXEC && event.sinkType() != SinkType.PROCESS_BUILDER_START) {
            return unmatched();
        }
        if (event.pathFrequency() < threshold) {
            return unmatched();
        }
        return matched(25, "Repeated process execution in a short period");
    }
}
