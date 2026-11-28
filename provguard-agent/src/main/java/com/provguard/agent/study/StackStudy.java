package com.provguard.agent.study;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.provguard.core.SinkType;
import com.provguard.ml.IsolationForestModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Collects instrumented JVM events and evaluates a rules baseline and two model variants. */
public final class StackStudy {
    private static final int SEED = 42;
    private static final double VALIDATION_FPR = 0.05;
    private static final String FIXTURE = "com.provguard.demo.safe.StudyWorkload";
    private static final String BENIGN = "com.provguard.demo.safe.SafeSerializableObject";
    private static final String POLICY_MARKER = "com.provguard.demo.unsafe.BlockedDemoObject";

    private record Observation(String signature, boolean positive, int ruleScore,
                               double[] context, double[] stackAndContext) {}

    private StackStudy() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: StackStudy <agent.jar> <demo.jar> <new-output-directory>");
        }
        Path agent = Path.of(args[0]).toAbsolutePath();
        Path demo = Path.of(args[1]).toAbsolutePath();
        Path output = Path.of(args[2]).toAbsolutePath();
        Files.createDirectory(output);
        var training = capture(agent, demo, output, "training", 60);
        var validation = capture(agent, demo, output, "validation", 60);
        var test = capture(agent, demo, output, "test", 120);
        requireDisjoint(training, validation, test);

        var full = IsolationForestModel.fit(training.stream().map(Observation::stackAndContext).toList(),
                new Random(SEED));
        var reduced = IsolationForestModel.fit(training.stream().map(Observation::context).toList(),
                new Random(SEED));
        ToIntFunction<Observation> fullScore = row -> full.score(row.stackAndContext());
        ToIntFunction<Observation> reducedScore = row -> reduced.score(row.context());
        int rulesThreshold = calibrate(validation, Observation::ruleScore);
        int fullThreshold = calibrate(validation, fullScore);
        int reducedThreshold = calibrate(validation, reducedScore);
        Predicate<Observation> rulesFlag = row -> row.ruleScore() >= rulesThreshold;
        int hybridThreshold = calibrate(validation, fullScore, rulesFlag);
        String report = """
                # ProvGuard JVM stack study

                JDK %s. Seed %d. Separate entry routes for training, validation and test.
                The study checks that no recorded path signature crosses those splits.
                Each split is one fresh JVM. All events are from one fixture, one sink and six call depths.
                Test positives are harmless denylisted class reads. Both classes use identical test routes.
                Thresholds were selected on benign validation to keep the observed false-positive rate at or below 5%%.
                A threshold of 101 disables alerts for that score. The hybrid retains all rules alerts
                and uses the reported threshold only for additional model alerts. Isolation Forest uses no class name,
                rule score, label, or call-path signature as input.

                | Detector | Threshold | TP | FP | TN | FN | Recall | FPR | Precision |
                | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
                %s
                %s
                %s
                %s

                This is a controlled policy benchmark. Iterations from one JVM and fixture are correlated.
                Results apply to this fixture and do not establish general JVM attack detection performance.
                """.formatted(Runtime.version(), SEED,
                metrics("Rules", rulesThreshold, test, rulesFlag),
                metrics("Isolation Forest without stack", reducedThreshold, test,
                        row -> reducedScore.applyAsInt(row) >= reducedThreshold),
                metrics("Isolation Forest with stack", fullThreshold, test,
                        row -> fullScore.applyAsInt(row) >= fullThreshold),
                metrics("Hybrid with stack", hybridThreshold, test, row ->
                        rulesFlag.test(row) || fullScore.applyAsInt(row) >= hybridThreshold));
        Files.writeString(output.resolve("results.md"), report);
        System.out.print(report);
    }

    private static List<Observation> capture(Path agent, Path demo, Path output, String split, int expected)
            throws IOException, InterruptedException {
        List<Observation> rows = new ArrayList<>();
        for (JsonNode event : captureEvents(agent, demo, output, split, FIXTURE, split, "")) {
            if (!"DESERIALIZATION_RESOLVE_CLASS".equals(event.path("sinkType").asText())) {
                continue;
            }
            String className = event.required("requestedClassName").asText();
            if (!BENIGN.equals(className) && !POLICY_MARKER.equals(className)) {
                throw new IOException("Unexpected fixture class: " + className);
            }
            rows.add(new Observation(event.required("pathSignature").asText(), POLICY_MARKER.equals(className),
                    event.required("ruleScore").asInt(), new double[]{SinkType.DESERIALIZATION_RESOLVE_CLASS.encoded()},
                    stackFeatures(event)));
        }
        long positives = rows.stream().filter(Observation::positive).count();
        if (rows.size() != expected || positives != (split.equals("test") ? 60 : 0)) {
            throw new IOException("Unexpected event or class count in " + split + ": " + rows.size() + "/" + positives);
        }
        return rows;
    }

    static double[] stackFeatures(JsonNode event) {
        return new double[] {
                SinkType.valueOf(event.required("sinkType").asText()).encoded(), event.required("stackDepth").asDouble(),
                event.required("applicationFrameCount").asDouble(), event.required("jdkFrameCount").asDouble(),
                event.required("externalLibraryFrameCount").asDouble(), event.required("uniquePackageCount").asDouble(),
                event.required("classLoaderCount").asDouble(), event.required("reflectionDetected").asBoolean() ? 1 : 0
        };
    }

    static List<JsonNode> captureEvents(Path agent, Path demo, Path output, String name, String fixture,
                                        String scenario, String extraAgentArguments)
            throws IOException, InterruptedException {
        return captureEvents(agent, demo, output, name, fixture, List.of(scenario), "MONITOR", extraAgentArguments);
    }

    static List<JsonNode> captureEvents(Path agent, Path demo, Path output, String name, String fixture,
                                        List<String> arguments, String mode, String extraAgentArguments)
            throws IOException, InterruptedException {
        Path events = output.resolve(name + ".jsonl");
        Path console = output.resolve(name + ".log");
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-javaagent:" + agent + "=mode=" + mode + ";verbose=false;events=" + events + extraAgentArguments,
                "-cp", demo.toString(), fixture));
        command.addAll(arguments);
        Process child = new ProcessBuilder(command)
                .redirectErrorStream(true).redirectOutput(console.toFile()).start();
        try {
            if (!child.waitFor(45, TimeUnit.SECONDS) || child.exitValue() != 0) {
                throw new IOException("Could not collect " + name + " events. See " + console);
            }
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> records = new ArrayList<>();
        for (String line : Files.readAllLines(events)) {
            records.add(mapper.readTree(line));
        }
        return records;
    }

    private static void requireDisjoint(List<Observation> training, List<Observation> validation, List<Observation> test) {
        Set<String> seen = new HashSet<>();
        for (var split : List.of(training, validation, test)) {
            var signatures = split.stream().map(Observation::signature).distinct().toList();
            if (signatures.stream().anyMatch(seen::contains)) {
                throw new IllegalStateException("A call-path signature appears in multiple splits");
            }
            seen.addAll(signatures);
        }
    }

    static <T> int calibrate(List<T> validation, ToIntFunction<T> score) {
        return calibrate(validation, score, row -> false);
    }

    private static <T> int calibrate(List<T> validation, ToIntFunction<T> score,
                                     Predicate<T> alreadyFlagged) {
        int[] counts = new int[101];
        int alerted = 0;
        for (T row : validation) {
            if (alreadyFlagged.test(row)) {
                alerted++;
            } else {
                counts[score.applyAsInt(row)]++;
            }
        }
        for (int threshold = 100; threshold >= 0; threshold--) {
            alerted += counts[threshold];
            if (alerted > validation.size() * VALIDATION_FPR) {
                return threshold + 1;
            }
        }
        return 0;
    }

    private static String metrics(String name, int threshold, List<Observation> rows, Predicate<Observation> flags) {
        long tp = rows.stream().filter(row -> row.positive() && flags.test(row)).count();
        long fp = rows.stream().filter(row -> !row.positive() && flags.test(row)).count();
        long positives = rows.stream().filter(Observation::positive).count();
        long fn = positives - tp;
        long tn = rows.size() - positives - fp;
        String precision = tp + fp == 0 ? "N/A" : percent(tp, tp + fp);
        return "| %s | %d | %d | %d | %d | %d | %s | %s | %s |".formatted(
                name, threshold, tp, fp, tn, fn, percent(tp, tp + fn), percent(fp, fp + tn), precision);
    }

    private static String percent(long count, long total) {
        return String.format(Locale.ROOT, "%.1f%%", 100.0 * count / total);
    }
}
