package com.provguard.agent;

import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;
import com.provguard.core.SecurityEventFactory;
import com.provguard.core.SinkType;
import com.provguard.core.exception.ProvGuardBlockedException;
import com.provguard.core.metadata.ArgumentMetadataCollector;
import com.provguard.core.metadata.ArgumentSnapshot;
import com.provguard.ml.FeatureExtractor;
import com.provguard.ml.FeatureVector;
import com.provguard.ml.MlPredictionResult;
import com.provguard.ml.MlScorer;
import com.provguard.ml.ResilientMlScorer;
import com.provguard.rules.HybridRiskAnalyzer;
import com.provguard.rules.PathFrequencyRule;
import com.provguard.rules.RiskRules;
import com.provguard.rules.RuleBasedRiskAnalyzer;
import com.provguard.storage.AlertReporter;
import com.provguard.storage.ConsoleReporter;
import com.provguard.storage.EventQueue;
import com.provguard.storage.EventStatistics;
import com.provguard.storage.JsonlEventWriter;
import com.provguard.storage.SecurityEventObserver;
import com.provguard.storage.SecurityEventWorker;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fast path on the application thread, slow path on one background worker.
 */
public final class EventPipeline implements AutoCloseable {
    private final AgentConfig config;
    private final PathFrequencyRule frequencyRule;
    private final RuleBasedRiskAnalyzer ruleAnalyzer;
    private final HybridRiskAnalyzer hybrid = new HybridRiskAnalyzer();
    private final FeatureExtractor features = new FeatureExtractor();
    private final MlScorer mlScorer;
    private final EventQueue queue;
    private final EventStatistics statistics = new EventStatistics();
    private final SecurityEventWorker worker;
    private final AtomicLong lastErrorAt = new AtomicLong();

    public static EventPipeline start(AgentConfig config) {
        PathFrequencyRule frequency = new PathFrequencyRule(
                Clock.systemUTC(),
                config.frequencyThreshold(),
                Duration.ofSeconds(10));
        RuleBasedRiskAnalyzer rules = new RuleBasedRiskAnalyzer(RiskRules.standard(
                config.knownPackages(),
                config.knownSignatures(),
                config.deserializationAllowlist(),
                config.deserializationDenylist(),
                frequency));
        EventQueue queue = new EventQueue(config.queueCapacity());
        JsonlEventWriter writer = new JsonlEventWriter(config.eventsFile(), config.mode());
        List<SecurityEventObserver> observers = new ArrayList<>();
        observers.add(writer);
        if (config.verbose()) {
            observers.add(new ConsoleReporter());
            observers.add(new AlertReporter());
        }
        return new EventPipeline(config, frequency, rules, queue, observers);
    }

    private EventPipeline(
            AgentConfig config,
            PathFrequencyRule frequencyRule,
            RuleBasedRiskAnalyzer ruleAnalyzer,
            EventQueue queue,
            List<SecurityEventObserver> observers) {
        this.config = config;
        this.frequencyRule = frequencyRule;
        this.ruleAnalyzer = ruleAnalyzer;
        this.queue = queue;
        this.mlScorer = ResilientMlScorer.create(config.mlUrl());
        SecurityEventWorker created = new SecurityEventWorker(queue, this::enrich, observers, statistics);
        this.worker = created;
        created.start();
    }

    public void onRuntimeExec(Object[] args) {
        guard(() -> {
            if (delegatedRuntimeExec()) {
                return;
            }
            submit(
                SinkType.RUNTIME_EXEC,
                "Runtime.exec",
                ArgumentMetadataCollector.fromCommandText(
                        args == null ? 0 : args.length,
                        args == null || args.length == 0 || args[0] == null ? "null" : args[0].getClass().getSimpleName(),
                        commandText(args)));
        });
    }

    private static boolean delegatedRuntimeExec() {
        return StackWalker.getInstance().walk(stream -> {
            var frames = stream
                    .filter(frame -> !frame.getClassName().startsWith("com.provguard."))
                    .limit(2)
                    .map(StackWalker.StackFrame::getClassName)
                    .toList();
            return frames.size() == 2
                    && "java.lang.Runtime".equals(frames.get(0))
                    && "java.lang.Runtime".equals(frames.get(1));
        });
    }

    public void onProcessBuilder(List<String> command) {
        String joined = command == null ? null : String.join(" ", command);
        guard(() -> submit(
                SinkType.PROCESS_BUILDER_START,
                "ProcessBuilder.start()",
                ArgumentMetadataCollector.fromCommandText(
                        command == null ? 0 : command.size(),
                        "List<String>",
                        joined)));
    }

    public void onResolveClass(String className) {
        guard(() -> submit(
                SinkType.DESERIALIZATION_RESOLVE_CLASS,
                "ObjectInputStream.resolveClass(ObjectStreamClass)",
                ArgumentMetadataCollector.fromClassName(className)));
    }

    public void submit(SinkType sinkType, String sinkMethod, ArgumentSnapshot snapshot) {
        StackTraceCollector.CapturedStack stack = StackTraceCollector.capture(config, sinkLabel(sinkType));
        int frequency = frequencyRule.observe(sinkType, stack.pathSignature());
        SecurityEvent event = create(sinkType, sinkMethod, snapshot, stack, frequency);
        RiskResult rules = ruleAnalyzer.analyze(event);
        MlPredictionResult ml = MlPredictionResult.unavailable("Scored asynchronously");
        if (config.mode() == com.provguard.core.AgentMode.BLOCK
                && rules.ruleScore() >= 60
                && rules.ruleScore() <= 79) {
            ml = score(event, rules.ruleScore(), Duration.ofMillis(200));
        }
        String status = ml.available() ? "AVAILABLE" : "ML_UNAVAILABLE";
        RiskResult decision = hybrid.combine(rules, ml.available() ? ml.score() : null, status, config.mode());
        boolean queued = queue.offer(new EventQueue.PendingEvent(event, decision));
        statistics.onCaptured(decision, queued);
        if (config.verbose()) {
            System.out.println("ProvGuard fast " + decision.decision()
                    + " enforced=" + decision.enforced()
                    + " sink=" + sinkType
                    + " ruleScore=" + decision.ruleScore()
                    + " path=" + event.pathSignature());
        }
        if (decision.enforced()) {
            throw new ProvGuardBlockedException(
                    "ProvGuard blocked " + sinkMethod
                            + " eventId=" + event.eventId()
                            + " ruleScore=" + decision.ruleScore()
                            + " rules=" + decision.matchedRules());
        }
    }

    public EventStatistics statistics() {
        return statistics;
    }

    private RiskResult enrich(SecurityEvent event, RiskResult fastResult) {
        MlPredictionResult ml = score(event, fastResult.ruleScore(), Duration.ofMillis(500));
        String status = ml.available() ? "AVAILABLE" : "ML_UNAVAILABLE";
        RiskResult combined = hybrid.combine(
                fastResult,
                ml.available() ? ml.score() : null,
                status,
                config.mode());
        return combined.withEnforced(fastResult.enforced());
    }

    private MlPredictionResult score(SecurityEvent event, int ruleScore, Duration timeout) {
        try {
            FeatureVector vector = features.extract(event, ruleScore);
            return mlScorer.score(vector, timeout);
        } catch (Throwable failure) {
            return MlPredictionResult.unavailable(failure.getMessage());
        }
    }

    private void guard(Runnable action) {
        if (RecursionGuard.isActive()) {
            return;
        }
        try {
            RecursionGuard.run(() -> {
                try {
                    action.run();
                } catch (ProvGuardBlockedException blocked) {
                    throw blocked;
                } catch (Throwable failure) {
                    report(failure);
                }
            });
        } catch (ProvGuardBlockedException blocked) {
            throw blocked;
        }
    }

    private SecurityEvent create(
            SinkType sinkType,
            String sinkMethod,
            ArgumentSnapshot snapshot,
            StackTraceCollector.CapturedStack stack,
            int frequency) {
        return switch (sinkType) {
            case RUNTIME_EXEC -> SecurityEventFactory.runtimeExec(
                    sinkMethod, stack.frames(), stack.pathSignature(), stack.pathFamily(), stack.counts(), snapshot, frequency);
            case PROCESS_BUILDER_START -> SecurityEventFactory.processBuilder(
                    sinkMethod, stack.frames(), stack.pathSignature(), stack.pathFamily(), stack.counts(), snapshot, frequency);
            case DESERIALIZATION_RESOLVE_CLASS -> SecurityEventFactory.deserialization(
                    sinkMethod, stack.frames(), stack.pathSignature(), stack.pathFamily(), stack.counts(), snapshot, frequency);
            default -> throw new IllegalArgumentException("Sink is not monitored in this version: " + sinkType);
        };
    }

    private static String sinkLabel(SinkType sinkType) {
        return switch (sinkType) {
            case RUNTIME_EXEC -> "Runtime.exec";
            case PROCESS_BUILDER_START -> "ProcessBuilder.start";
            case DESERIALIZATION_RESOLVE_CLASS -> "ObjectInputStream.resolveClass";
            default -> sinkType.name();
        };
    }

    static String commandText(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        if (args[0] instanceof String text) {
            return text;
        }
        if (args[0] instanceof String[] command) {
            return ArgumentMetadataCollector.joinCommand(command);
        }
        return null;
    }

    private void report(Throwable failure) {
        long now = System.currentTimeMillis();
        long previous = lastErrorAt.get();
        if (now - previous < 5_000 || !lastErrorAt.compareAndSet(previous, now)) {
            return;
        }
        System.err.println("ProvGuard ignored an internal error and allowed the call. " + failure.getMessage());
    }

    @Override
    public void close() {
        try {
            worker.close();
        } finally {
            System.out.println("ProvGuard statistics " + statistics.summary());
        }
    }
}
