package com.provguard.web;

import com.provguard.ml.IsolationForestModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Describes the Isolation Forest packaged with this build and reads the offline evaluation report if present.
 * Whether a particular running agent used the model is recorded per event in {@code modelStatus}, not here.
 */
public final class ModelInfo {
    private ModelInfo() {
    }

    public static Map<String, Object> describe(Path reportsDir) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("packaged", packaged());
        out.put("scoreMeaning", "A 0-100 anomaly score. The raw Isolation Forest score is rescaled so the 5th percentile of benign training "
                + "rows maps to 0 and the 99th percentile to 100. It is a score, not a probability of attack.");
        out.put("liveScoringNote", "Live scoring is done by the agent. The rule score decides blocking; a high model score alone alerts and never blocks.");
        out.put("evaluation", evaluation(reportsDir));
        return out;
    }

    private static Map<String, Object> packaged() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            IsolationForestModel model = IsolationForestModel.bundled();
            if (model == null) {
                out.put("status", "unavailable");
                out.put("detail", "The Isolation Forest resource is not on this backend's classpath.");
                return out;
            }
            out.put("status", "loaded");
            out.put("type", "Isolation Forest (Java implementation, provguard-ml)");
            out.put("trees", model.trees().length);
            out.put("sampleSize", model.sampleSize());
            out.put("scaleLowRaw", round(model.scoreLow()));
            out.put("scaleHighRaw", round(model.scoreHigh()));
            out.put("detail", "Loaded from the packaged resource. This confirms the model ships with the build, not that a given agent run used it.");
        } catch (Throwable failure) {
            out.put("status", "unavailable");
            out.put("detail", "The packaged model could not be read: " + failure.getClass().getSimpleName());
        }
        return out;
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    static Map<String, Object> evaluation(Path reportsDir) {
        Map<String, Object> out = new LinkedHashMap<>();
        Path file = reportsDir == null ? null : reportsDir.resolve("ml-evaluation.md");
        out.put("source", "reports/ml-evaluation.md");
        out.put("scope", "Offline evaluation on the BETH sample plus recorded safe demo shapes. It is not a measure of live detection.");
        if (file == null || !Files.isRegularFile(file)) {
            out.put("available", false);
            out.put("detail", "ml-evaluation.md was not found. Start the dashboard from the repository root or pass --reports <dir>.");
            out.put("metrics", List.of());
            out.put("notReported", List.of("Precision", "F1"));
            return out;
        }
        try {
            if (Files.size(file) > 256 * 1024) {
                throw new IOException("report too large");
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<Map<String, String>> metrics = new ArrayList<>();
            StringBuilder description = new StringBuilder();
            boolean sawBullets = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("- ") && trimmed.contains(": ")) {
                    sawBullets = true;
                    String body = trimmed.substring(2);
                    int colon = body.indexOf(": ");
                    String label = body.substring(0, colon).replace("`", "").trim();
                    String value = body.substring(colon + 2).replace("`", "").trim();
                    if (!label.isEmpty() && label.length() <= 80) {
                        metrics.add(Map.of("label", label, "value", EventParser.clean(value, 600)));
                    }
                } else if (!sawBullets && !trimmed.isEmpty() && !trimmed.startsWith("#") && description.length() < 900) {
                    description.append(trimmed.replace("`", "")).append(' ');
                }
            }
            out.put("available", true);
            out.put("lastModifiedMs", Files.getLastModifiedTime(file).toMillis());
            out.put("description", description.toString().trim());
            out.put("metrics", metrics);
            List<String> missing = new ArrayList<>();
            for (String wanted : new String[] {"Precision", "F1"}) {
                boolean present = metrics.stream()
                        .anyMatch(m -> m.get("label").toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT)));
                if (!present) {
                    missing.add(wanted);
                }
            }
            out.put("notReported", missing);
        } catch (IOException exception) {
            out.put("available", false);
            out.put("detail", "ml-evaluation.md could not be read: " + exception.getClass().getSimpleName());
            out.put("metrics", List.of());
            out.put("notReported", List.of("Precision", "F1"));
        }
        return out;
    }
}
