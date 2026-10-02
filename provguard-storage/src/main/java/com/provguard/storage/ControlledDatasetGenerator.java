package com.provguard.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * Writes a controlled feature dataset.
 * Rows are grouped by call-path family so training and test sets do not share a family.
 */
public final class ControlledDatasetGenerator {
    public static void main(String[] args) throws IOException {
        Path output = args.length == 0 ? Path.of("ml-training", "data", "controlled-events.csv") : Path.of(args[0]);
        write(output, new Random(42));
        System.out.println("Wrote " + output.toAbsolutePath());
    }

    public static void write(Path output, Random random) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder csv = new StringBuilder();
        csv.append("event_id,timestamp,sink_type,stack_depth,application_frame_count,jdk_frame_count,");
        csv.append("external_library_frame_count,unique_package_count,reflection_detected,argument_length,");
        csv.append("keyword_risk_score,path_frequency,class_loader_count,rule_score,path_family,label,split\n");
        int id = 1;
        id = benign(csv, random, id, "safe-echo", 180, "train", 0);
        id = benign(csv, random, id, "safe-runtime", 120, "train", 1);
        id = benign(csv, random, id, "safe-deserialize", 80, "train", 2);
        id = benign(csv, random, id, "heldout-safe-batch", 40, "test", 1);
        id = suspicious(csv, random, id, "suspicious-keyword", 40, 55, 0, 8, 70);
        id = suspicious(csv, random, id, "suspicious-reflection", 30, 35, 1, 12, 75);
        id = suspicious(csv, random, id, "suspicious-deserialize", 20, 0, 0, 6, 80);
        suspicious(csv, random, id, "suspicious-frequency", 20, 35, 0, 4, 60);
        Files.writeString(output, csv.toString(), StandardCharsets.UTF_8);
    }

    private static int benign(StringBuilder csv, Random random, int id, String family, int rows, String split, int sink) {
        for (int i = 0; i < rows; i++) {
            row(csv, id++, "2026-10-01T00:00:00Z", sinkName(sink),
                    4 + random.nextInt(3),
                    2 + random.nextInt(2),
                    2 + random.nextInt(2),
                    random.nextInt(2),
                    2 + random.nextInt(2),
                    0,
                    10 + random.nextInt(30),
                    0,
                    1 + random.nextInt(2),
                    1,
                    random.nextInt(15),
                    family,
                    "BENIGN",
                    split);
        }
        return id;
    }

    private static int suspicious(StringBuilder csv, Random random, int id, String family, int rows,
                                  int keyword, int reflection, int depth, int rule) {
        for (int i = 0; i < rows; i++) {
            row(csv, id++, "2026-10-01T01:00:00Z", "PROCESS_BUILDER_START",
                    depth + random.nextInt(4),
                    1 + random.nextInt(2),
                    3 + random.nextInt(3),
                    2 + random.nextInt(3),
                    3 + random.nextInt(3),
                    reflection,
                    40 + random.nextInt(80),
                    keyword,
                    5 + random.nextInt(4),
                    2,
                    Math.min(100, rule + random.nextInt(10)),
                    family,
                    "SUSPICIOUS",
                    "test");
        }
        return id;
    }

    private static String sinkName(int sink) {
        return switch (sink) {
            case 0 -> "PROCESS_BUILDER_START";
            case 1 -> "RUNTIME_EXEC";
            default -> "DESERIALIZATION_RESOLVE_CLASS";
        };
    }

    private static void row(StringBuilder csv, int id, String timestamp, String sink, int depth, int app, int jdk,
                            int external, int packages, int reflection, int length, int keyword, int frequency,
                            int loaders, int rule, String family, String label, String split) {
        csv.append(id).append(',').append(timestamp).append(',').append(sink).append(',')
                .append(depth).append(',').append(app).append(',').append(jdk).append(',')
                .append(external).append(',').append(packages).append(',').append(reflection).append(',')
                .append(length).append(',').append(keyword).append(',').append(frequency).append(',')
                .append(loaders).append(',').append(rule).append(',').append(family).append(',')
                .append(label).append(',').append(split).append('\n');
    }
}
