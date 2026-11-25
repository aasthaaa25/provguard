package com.provguard.agent.study;

import com.fasterxml.jackson.databind.JsonNode;
import com.provguard.ml.IsolationForestModel;
import com.provguard.rules.BlockingPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/** Leave-one-workload-out comparison of method order, unordered methods and aggregate stack counts. */
public final class OrderedPathStudy {
    private static final List<String> WORKLOADS = List.of("Cache", "Import", "Replay");
    private static final int ROWS_PER_SCENARIO = 24;
    private static final String FIXTURE = "com.provguard.demo.safe.OrderedWorkloads$";
    private static final List<String> DETECTORS = List.of(
            "Rules", "Aggregate Isolation Forest", "Unordered method profile", "Ordered method profile",
            "Virtual path profile", "Rules + both path profiles");

    private record Observation(String eventId, String argumentHash, OrderedPathProfile.MethodPath path,
                               double[] features, int ruleScore) {}
    private record Workload(String name, List<Observation> nominal, List<Observation> reordered,
                            List<Observation> sequence, List<Observation> drift) {}

    private static final class Counts {
        int tp;
        int sequenceTp;
        int nominalFp;
        int driftFp;

        void add(Counts other) {
            tp += other.tp;
            sequenceTp += other.sequenceTp;
            nominalFp += other.nominalFp;
            driftFp += other.driftFp;
        }
    }

    private OrderedPathStudy() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: OrderedPathStudy <agent.jar> <demo.jar> <new-output-directory>");
        }
        Path agent = Path.of(args[0]).toAbsolutePath();
        Path demo = Path.of(args[1]).toAbsolutePath();
        Path output = Path.of(args[2]).toAbsolutePath();
        Files.createDirectory(output);
        Workload[] workloads = new Workload[WORKLOADS.size()];
        for (int i = 0; i < workloads.length; i++) {
            String name = WORKLOADS.get(i);
            workloads[i] = new Workload(name, capture(agent, demo, output, name, "nominal"),
                    capture(agent, demo, output, name, "reordered"), capture(agent, demo, output, name, "sequence"),
                    capture(agent, demo, output, name, "drift"));
            assertMatchedPairs(workloads[i]);
        }

        List<Counts> totals = DETECTORS.stream().map(name -> new Counts()).toList();
        StringBuilder folds = new StringBuilder();
        StringBuilder predictions = new StringBuilder(
                "train,validation,test,scenario,event_id,rules,aggregate,unordered,ordered,virtual,hybrid\n");
        for (int i = 0; i < workloads.length; i++) {
            Workload train = workloads[i];
            Workload validation = workloads[(i + 1) % workloads.length];
            Workload test = workloads[(i + 2) % workloads.length];
            var ordered = profile(train.nominal(), false);
            var unordered = profile(train.nominal(), true);
            var aggregate = IsolationForestModel.fit(train.nominal().stream().map(Observation::features).toList(),
                    new Random(42));
            int aggregateThreshold = StackStudy.calibrate(validation.nominal(), row -> aggregate.score(row.features()));
            int orderedThreshold = StackStudy.calibrate(validation.nominal(), row -> novelty(ordered, row.path()));
            int unorderedThreshold = StackStudy.calibrate(validation.nominal(),
                    row -> novelty(unordered, row.path().unordered()));
            var trainingVirtual = new HashSet<>(virtualPaths(train.nominal()));
            var validationVirtual = virtualPaths(validation.nominal());
            int virtualThreshold = StackStudy.calibrate(validationVirtual,
                    transition -> trainingVirtual.contains(transition) ? 0 : 100);
            Predicate<Observation> ruleFlag = row -> row.ruleScore() >= BlockingPolicy.MEDIUM_RULE_SCORE;
            Predicate<Observation> orderFlag = row -> novelty(ordered, row.path()) >= orderedThreshold;
            List<Predicate<Observation>> detectors = List.of(ruleFlag,
                    row -> aggregate.score(row.features()) >= aggregateThreshold,
                    row -> novelty(unordered, row.path().unordered()) >= unorderedThreshold,
                    orderFlag);
            List<Counts> fold = DETECTORS.stream().map(name -> new Counts()).toList();
            Predicate<OrderedPathProfile.VirtualPath> virtualFlag =
                    transition -> (trainingVirtual.contains(transition) ? 0 : 100) >= virtualThreshold;
            score(train, validation, test, "nominal", test.nominal(), detectors, virtualFlag, fold, predictions);
            score(train, validation, test, "reordered", test.reordered(), detectors, virtualFlag, fold, predictions);
            score(train, validation, test, "sequence", test.sequence(), detectors, virtualFlag, fold, predictions);
            score(train, validation, test, "drift", test.drift(), detectors, virtualFlag, fold, predictions);
            for (int method = 0; method < DETECTORS.size(); method++) {
                totals.get(method).add(fold.get(method));
            }
            Counts pathCounts = fold.get(3);
            folds.append("| %s | %s | %s | %d | %d | %d | %d | %d | %d |%n".formatted(
                    train.name(), validation.name(), test.name(), aggregateThreshold, unorderedThreshold,
                    orderedThreshold, virtualThreshold, pathCounts.tp, fold.get(4).sequenceTp));
        }
        int count = workloads.length * ROWS_PER_SCENARIO;
        StringBuilder metrics = new StringBuilder();
        for (int i = 0; i < DETECTORS.size(); i++) {
            Counts result = totals.get(i);
            metrics.append(String.format(Locale.ROOT, "| %s | %d/%d | %d/%d | %d/%d | %d/%d | %.1f%% |%n",
                    DETECTORS.get(i), result.tp, count, result.sequenceTp, count, result.nominalFp, count,
                    result.driftFp, count, 100.0 * (result.nominalFp + result.driftFp) / (2 * count)));
        }
        String report = """
                # Ordered JVM method-path study

                JDK %s. Isolation Forest seed 42. Three designed workload variants and twelve fresh instrumented JVMs.
                Each fold learns on one nominal workload, sets thresholds on another, and tests the third.
                Test labels come from the launch scenario. All cases deserialize the same harmless class.
                Reordered pairs share rule score, safe argument hash, method multiset, and aggregate stack features.
                Reordered cases swap application-method order inside each stack. The sequence scenario
                permutes existing snapshots across events. The per-event denominator includes transitions
                left intact by that permutation. Drift adds a legitimate wrapper.
                Virtual paths retain the common-prefix depth and the exits and entries between consecutive
                observed snapshots on the same thread. Each fixture run starts with an empty snapshot.
                A method token is the unqualified method name in this cross-variant study. This deliberately shares
                role names across fixture classes. The standalone CLI uses qualified names for same-application audits.
                Negative and positive examples are paired by launch iteration and checked before scoring.
                Rules use the existing alert threshold of 30. Frequency threshold is 1000 for all captures.
                Model thresholds target at most 5%% nominal validation false alarms. Score 101 disables alerts.

                | Detector | Reordered alerts | Sequence alerts | Nominal false alarms | Drift false alarms | FPR across both benign scenarios |
                | --- | ---: | ---: | ---: | ---: | ---: |
                %s
                | Train | Validate | Test | Aggregate threshold | Unordered threshold | Ordered threshold | Virtual threshold | Ordered reorder alerts | Virtual sequence alerts |
                | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
                %s
                Raw events and predictions.csv accompany this report. The repeated observations from each workload
                are correlated. The three fixture variants share the serialization harness, method vocabulary and
                constructed perturbation. This is a controlled mechanism experiment with a deliberate drift stress
                case. Its rates do not estimate performance on production applications.
                """.formatted(Runtime.version(), metrics, folds);
        Files.writeString(output.resolve("ordered-results.md"), report);
        Files.writeString(output.resolve("predictions.csv"), predictions);
        System.out.print(report);
    }

    private static List<Observation> capture(Path agent, Path demo, Path output, String name, String scenario)
            throws IOException, InterruptedException {
        var events = StackStudy.captureEvents(agent, demo, output, name + "-" + scenario, FIXTURE + name, scenario,
                ";frequencyThreshold=1000");
        if (events.size() != ROWS_PER_SCENARIO) {
            throw new IOException("Unexpected number of records for " + name + "/" + scenario + ": " + events.size());
        }
        for (JsonNode event : events) {
            if (!"DESERIALIZATION_RESOLVE_CLASS".equals(event.required("sinkType").asText())
                    || !"com.provguard.demo.safe.SafeSerializableObject".equals(event.required("requestedClassName").asText())
                    || event.required("threadId").asLong() != events.getFirst().required("threadId").asLong()
                    || event.required("enforced").asBoolean()) {
                throw new IOException("Unexpected telemetry for " + name + "/" + scenario);
            }
        }
        return events.stream().map(event -> new Observation(event.required("eventId").asText(),
                event.required("argumentHash").asText(),
                OrderedPathProfile.path(event, false), StackStudy.stackFeatures(event),
                event.required("ruleScore").asInt())).toList();
    }

    private static void assertMatchedPairs(Workload workload) {
        for (int i = 0; i < workload.nominal().size(); i++) {
            Observation a = workload.nominal().get(i);
            Observation b = workload.reordered().get(i);
            if (a.path().equals(b.path()) || !a.path().unordered().equals(b.path().unordered())
                    || !a.argumentHash().equals(b.argumentHash()) || a.ruleScore() != b.ruleScore()
                    || !Arrays.equals(a.features(), b.features())) {
                throw new IllegalStateException("Unmatched control/perturbation pair in " + workload.name() + " at " + i);
            }
        }
        if (!pathMultiset(workload.nominal()).equals(pathMultiset(workload.sequence()))) {
            throw new IllegalStateException("Sequence fixture changed individual snapshots in " + workload.name());
        }
    }

    private static java.util.Map<OrderedPathProfile.MethodPath, Long> pathMultiset(List<Observation> rows) {
        return rows.stream().collect(java.util.stream.Collectors.groupingBy(Observation::path,
                java.util.stream.Collectors.counting()));
    }

    private static List<OrderedPathProfile.VirtualPath> virtualPaths(List<Observation> rows) {
        return IntStream.range(0, rows.size()).mapToObj(i -> OrderedPathProfile.VirtualPath.between(
                i == 0 ? null : rows.get(i - 1).path(), rows.get(i).path())).toList();
    }

    private static OrderedPathProfile profile(List<Observation> training, boolean unordered) {
        var paths = new HashSet<OrderedPathProfile.MethodPath>();
        for (Observation row : training) {
            paths.add(unordered ? row.path().unordered() : row.path());
        }
        return new OrderedPathProfile(false, paths);
    }

    private static int novelty(OrderedPathProfile profile, OrderedPathProfile.MethodPath path) {
        return "UNSEEN".equals(profile.classify(path)) ? 100 : 0;
    }

    private static void score(Workload train, Workload validation, Workload test, String scenario,
                              List<Observation> rows, List<Predicate<Observation>> detectors,
                              Predicate<OrderedPathProfile.VirtualPath> virtualFlag,
                              List<Counts> counts, StringBuilder predictions) {
        var transitions = virtualPaths(rows);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Observation row = rows.get(rowIndex);
            predictions.append(String.join(",", train.name(), validation.name(), test.name(), scenario, row.eventId()));
            boolean transitionFlagged = virtualFlag.test(transitions.get(rowIndex));
            boolean[] flags = {detectors.get(0).test(row), detectors.get(1).test(row), detectors.get(2).test(row),
                    detectors.get(3).test(row), transitionFlagged, false};
            flags[5] = flags[0] || flags[3] || flags[4];
            for (int i = 0; i < flags.length; i++) {
                boolean flagged = flags[i];
                predictions.append(flagged ? ",1" : ",0");
                if (flagged) {
                    Counts result = counts.get(i);
                    switch (scenario) {
                        case "reordered" -> result.tp++;
                        case "sequence" -> result.sequenceTp++;
                        case "nominal" -> result.nominalFp++;
                        case "drift" -> result.driftFp++;
                        default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
                    }
                }
            }
            predictions.append('\n');
        }
    }
}
