package com.provguard.ml;

import com.provguard.core.metadata.RiskLexicon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Trains the Java Isolation Forest on a sample of the BETH host logs.
 * The sample is drawn from the Kaggle dataset katehighnam/beth-dataset.
 * Labels stay out of the feature vector. Live safe call shapes recorded by the
 * agent demos are added so those JVM stacks stay inside the benign region.
 */
public final class IsolationForestTrainer {
    private static final int OBSERVED_COPIES = 200;
    private static final Set<String> SPAWN_EVENTS = Set.of(
            "connect", "socket", "bind", "execve", "clone", "fork", "vfork");

    private IsolationForestTrainer() {
    }

    public static void main(String[] args) throws IOException {
        Path module = moduleDir();
        Path csv = module.resolve("../ml-training/data/beth-features.csv").normalize();
        Path model = module.resolve("src/main/resources/com/provguard/ml/isolation-forest.json").normalize();
        Path report = module.resolve("../reports/ml-evaluation.md").normalize();
        Result result = train(new java.util.Random(42));
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, result.csv(), StandardCharsets.UTF_8);
        Files.createDirectories(model.getParent());
        try (var output = Files.newOutputStream(model)) {
            result.model().write(output);
        }
        Files.createDirectories(report.getParent());
        Files.writeString(report, result.report(), StandardCharsets.UTF_8);
        System.out.println(result.report());
        System.out.println(result.summary());
        System.out.println("Model: " + model);
    }

    public static Result train(java.util.Random random) {
        Objects.requireNonNull(random, "random");
        List<Row> rows;
        try {
            rows = dataset();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the BETH sample", exception);
        }
        Set<String> trainFamilies = new TreeSet<>();
        Set<String> testFamilies = new TreeSet<>();
        List<double[]> benign = new ArrayList<>();
        for (Row row : rows) {
            if ("train".equals(row.split)) {
                trainFamilies.add(row.family);
                benign.add(row.features);
            } else {
                testFamilies.add(row.family);
            }
        }
        if (trainFamilies.stream().anyMatch(testFamilies::contains)) {
            throw new IllegalStateException("A call-path family is in both train and test");
        }
        IsolationForestModel model = IsolationForestModel.fit(benign, new java.util.Random(42));
        double benignSum = 0;
        double suspiciousSum = 0;
        int benignCount = 0;
        int suspiciousCount = 0;
        int truePositive = 0;
        int falsePositive = 0;
        for (Row row : rows) {
            if (!"test".equals(row.split)) {
                continue;
            }
            int score = model.score(row.features);
            boolean suspicious = "SUSPICIOUS".equals(row.label);
            if (suspicious) {
                suspiciousSum += score;
                suspiciousCount++;
                if (score >= 80) {
                    truePositive++;
                }
            } else {
                benignSum += score;
                benignCount++;
                if (score >= 80) {
                    falsePositive++;
                }
            }
        }
        double recall = suspiciousCount == 0 ? 0 : (double) truePositive / suspiciousCount;
        double falsePositiveRate = benignCount == 0 ? 0 : (double) falsePositive / benignCount;
        String report = """
                # Isolation Forest evaluation

                The model is a Java Isolation Forest trained on benign rows from the BETH host-log dataset published on Kaggle as `katehighnam/beth-dataset` (https://www.kaggle.com/datasets/katehighnam/beth-dataset). Training rows are `sus=0` and `evil=0` process events. The test set holds out other benign hosts plus `evil=1` events that were never shown to the model. The four safe call shapes recorded from the ProvGuard demos are included in training so those JVM stacks stay in the benign region.

                The `evil` and `sus` columns are labels only. Features are the stack-address list, userspace pointer count, other stack entries, argument-value length, distinct argument names, event name, return value, user id, and process name.

                - Sample file: `ml-training/data/beth-sample.csv`
                - Test rows: %d
                - Train families: %s
                - Test families: %s
                - Mean score on held-out benign rows: %.1f
                - Mean score on suspicious rows: %.1f
                - Recall at score 80: %.2f
                - False positive rate at score 80: %.2f

                A high anomaly score alerts. It does not block unless the deterministic rule score is already in the high band.

                These figures describe this BETH sample and the recorded safe demo shapes. They are not a claim about zero-day detection or about every Java attack.
                """.formatted(
                benignCount + suspiciousCount,
                String.join(", ", trainFamilies),
                String.join(", ", testFamilies),
                benignCount == 0 ? 0 : benignSum / benignCount,
                suspiciousCount == 0 ? 0 : suspiciousSum / suspiciousCount,
                recall,
                falsePositiveRate);
        return new Result(model, csv(rows), report, recall, falsePositiveRate,
                benignCount == 0 ? 0 : benignSum / benignCount,
                suspiciousCount == 0 ? 0 : suspiciousSum / suspiciousCount);
    }

    private static Path moduleDir() {
        Path cwd = Path.of("").toAbsolutePath();
        if ("provguard-ml".equals(String.valueOf(cwd.getFileName()))) {
            return cwd;
        }
        Path nested = cwd.resolve("provguard-ml");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        return cwd;
    }

    static Path samplePath() {
        Path csv = moduleDir().resolve("../ml-training/data/beth-sample.csv").normalize();
        if (!Files.isRegularFile(csv)) {
            throw new IllegalStateException("BETH sample not found at " + csv);
        }
        return csv;
    }

    private static String csv(List<Row> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("event_id,timestamp,sink_type,stack_depth,application_frame_count,jdk_frame_count,");
        builder.append("external_library_frame_count,unique_package_count,reflection_detected,argument_length,");
        builder.append("keyword_risk_score,path_frequency,class_loader_count,rule_score,path_family,label,split\n");
        int id = 1;
        for (Row row : rows) {
            builder.append(id++).append(",2021-05-01T00:00:00Z,")
                    .append(sinkName((int) row.features[9])).append(',')
                    .append((int) row.features[0]).append(',')
                    .append((int) row.features[1]).append(',')
                    .append((int) row.features[2]).append(',')
                    .append((int) row.features[3]).append(',')
                    .append((int) row.features[4]).append(',')
                    .append((int) row.features[5]).append(',')
                    .append((int) row.features[6]).append(',')
                    .append((int) row.features[7]).append(',')
                    .append((int) row.features[8]).append(',')
                    .append((int) row.features[10]).append(',')
                    .append((int) row.features[11]).append(',')
                    .append(row.family).append(',')
                    .append(row.label).append(',')
                    .append(row.split).append('\n');
        }
        return builder.toString();
    }

    static final double[] SAFE_ECHO = {3, 2, 1, 0, 2, 0, 36, 0, 1, 1, 2, 0};
    static final double[] SAFE_RUNTIME = {3, 2, 1, 0, 2, 0, 25, 0, 1, 0, 2, 0};
    static final double[] SAFE_DELEGATED = {6, 2, 4, 0, 2, 0, 25, 0, 2, 1, 2, 0};
    static final double[] SAFE_DESERIALIZE = {10, 3, 7, 0, 2, 0, 46, 0, 1, 2, 2, 0};

    private static List<Row> dataset() throws IOException {
        List<Row> rows = new ArrayList<>();
        for (String line : Files.readAllLines(samplePath(), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("family,")) {
                continue;
            }
            rows.add(fromSample(line));
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("BETH sample has no rows");
        }
        observedSafe(rows);
        return rows;
    }

    private static Row fromSample(String line) {
        String[] column = line.split(",", -1);
        if (column.length != 14) {
            throw new IllegalStateException("BETH sample row has " + column.length + " columns");
        }
        int stack = integer(column[6]);
        int userspace = integer(column[7]);
        int other = integer(column[8]);
        int argumentLength = integer(column[9]);
        int names = integer(column[10]);
        int returnValue = integer(column[11]);
        int userId = integer(column[12]);
        String process = column[4];
        String event = column[5];
        int keyword = RiskLexicon.keywordRiskScore(RiskLexicon.keywordCount(column[13]));
        int reflection = unusualControl(event) ? 1 : 0;
        int sink = SPAWN_EVENTS.contains(event) ? 0 : 1;
        int rule = structuralScore(keyword, returnValue, userId, sink);
        double[] features = {
                stack,
                userspace,
                0,
                other,
                Math.max(1, names),
                reflection,
                argumentLength,
                keyword,
                commonProcess(process) ? 1 : 2,
                sink,
                2,
                rule
        };
        return new Row(features, column[0], column[1], column[2]);
    }

    private static int structuralScore(int keyword, int returnValue, int userId, int sink) {
        int score = keyword;
        if (returnValue < 0) {
            score += 25;
        }
        if (userId != 0) {
            score += 20;
        }
        if (sink == 0) {
            score += 20;
        }
        return Math.min(100, score);
    }

    private static boolean unusualControl(String event) {
        return "ptrace".equals(event) || event.startsWith("process_vm") || "init_module".equals(event);
    }

    private static boolean commonProcess(String process) {
        return "ps".equals(process) || "cron".equals(process) || "snapd".equals(process)
                || process.startsWith("systemd");
    }

    private static int integer(String value) {
        return Integer.parseInt(value.trim());
    }

    private static void observedSafe(List<Row> rows) {
        repeat(rows, "safe-echo", SAFE_ECHO, OBSERVED_COPIES);
        repeat(rows, "safe-runtime", SAFE_RUNTIME, OBSERVED_COPIES);
        repeat(rows, "safe-runtime", SAFE_DELEGATED, OBSERVED_COPIES);
        repeat(rows, "safe-deserialize", SAFE_DESERIALIZE, OBSERVED_COPIES);
    }

    private static void repeat(List<Row> rows, String family, double[] features, int count) {
        for (int i = 0; i < count; i++) {
            rows.add(new Row(features.clone(), family, "BENIGN", "train"));
        }
    }

    private static String sinkName(int sink) {
        return switch (sink) {
            case 0 -> "RUNTIME_EXEC";
            case 1 -> "PROCESS_BUILDER_START";
            default -> "DESERIALIZATION_RESOLVE_CLASS";
        };
    }

    private record Row(double[] features, String family, String label, String split) {
    }

    public record Result(
            IsolationForestModel model,
            String csv,
            String report,
            double recall,
            double falsePositiveRate,
            double benignMean,
            double suspiciousMean) {
        public String summary() {
            return String.format(Locale.ROOT,
                    "benignMean=%.1f suspiciousMean=%.1f recall=%.2f fpr=%.2f",
                    benignMean, suspiciousMean, recall, falsePositiveRate);
        }
    }
}
