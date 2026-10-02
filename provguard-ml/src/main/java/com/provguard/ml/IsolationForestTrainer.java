package com.provguard.ml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Builds the controlled dataset, trains the Java Isolation Forest, and writes the evaluation.
 */
public final class IsolationForestTrainer {
    private IsolationForestTrainer() {
    }

    public static void main(String[] args) throws IOException {
        Path module = moduleDir();
        Path csv = module.resolve("../ml-training/data/controlled-events.csv").normalize();
        Path model = module.resolve("src/main/resources/com/provguard/ml/isolation-forest.json").normalize();
        Path report = module.resolve("../reports/ml-evaluation.md").normalize();
        Result result = train(new Random(42));
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, result.csv(), StandardCharsets.UTF_8);
        Files.createDirectories(model.getParent());
        try (var output = Files.newOutputStream(model)) {
            result.model().write(output);
        }
        Files.createDirectories(report.getParent());
        Files.writeString(report, result.report(), StandardCharsets.UTF_8);
        System.out.println(result.report());
        System.out.println("Model: " + model);
    }

    public static Result train(Random random) {
        List<Row> rows = dataset(random);
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
        IsolationForestModel model = IsolationForestModel.fit(benign, new Random(42));
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

                The model is a Java Isolation Forest trained on benign call-path families only. The test set is a held-out benign family plus suspicious families that were never shown to the model.

                - Test rows: %d
                - Train families: %s
                - Test families: %s
                - Mean score on held-out benign rows: %.1f
                - Mean score on suspicious rows: %.1f
                - Recall at score 80: %.2f
                - False positive rate at score 80: %.2f

                A high anomaly score alerts. It does not block unless the deterministic rule score is already in the high band.

                These figures describe this controlled dataset. They are not a claim about zero-day detection or about every Java attack.
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

    private static String csv(List<Row> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("event_id,timestamp,sink_type,stack_depth,application_frame_count,jdk_frame_count,");
        builder.append("external_library_frame_count,unique_package_count,reflection_detected,argument_length,");
        builder.append("keyword_risk_score,path_frequency,class_loader_count,rule_score,path_family,label,split\n");
        int id = 1;
        for (Row row : rows) {
            builder.append(id++).append(",2026-10-01T00:00:00Z,")
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

    private static List<Row> dataset(Random random) {
        List<Row> rows = new ArrayList<>();
        benign(rows, random, "safe-echo", 180, "train", 0);
        benign(rows, random, "safe-runtime", 120, "train", 1);
        benign(rows, random, "safe-deserialize", 80, "train", 2);
        benign(rows, random, "heldout-safe-batch", 40, "test", 1);
        suspicious(rows, random, "suspicious-keyword", 40, 55, 0, 70);
        suspicious(rows, random, "suspicious-reflection", 30, 35, 1, 75);
        suspicious(rows, random, "suspicious-deserialize", 20, 0, 0, 80);
        suspicious(rows, random, "suspicious-frequency", 20, 35, 0, 60);
        return rows;
    }

    private static void benign(List<Row> rows, Random random, String family, int count, String split, int sink) {
        for (int i = 0; i < count; i++) {
            rows.add(new Row(new double[] {
                    4 + random.nextInt(3),
                    2 + random.nextInt(2),
                    2 + random.nextInt(2),
                    random.nextInt(2),
                    2 + random.nextInt(2),
                    0,
                    10 + random.nextInt(30),
                    0,
                    1 + random.nextInt(2),
                    sink,
                    1,
                    random.nextInt(15)
            }, family, "BENIGN", split));
        }
    }

    private static void suspicious(List<Row> rows, Random random, String family, int count,
                                   int keyword, int reflection, int rule) {
        for (int i = 0; i < count; i++) {
            rows.add(new Row(new double[] {
                    8 + random.nextInt(4),
                    1 + random.nextInt(2),
                    3 + random.nextInt(3),
                    2 + random.nextInt(3),
                    3 + random.nextInt(3),
                    reflection,
                    40 + random.nextInt(80),
                    keyword,
                    5 + random.nextInt(4),
                    1,
                    2,
                    Math.min(100, rule + random.nextInt(10))
            }, family, "SUSPICIOUS", "test"));
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
