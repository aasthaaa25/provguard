package com.provguard.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ReportGenerator {
    private static final List<String> COLUMNS = List.of(
            "event_id",
            "timestamp",
            "sink_type",
            "path_family",
            "path_signature",
            "stack_depth",
            "application_frame_count",
            "jdk_frame_count",
            "external_library_frame_count",
            "unique_package_count",
            "reflection_detected",
            "argument_length",
            "keyword_risk_score",
            "path_frequency",
            "class_loader_count",
            "rule_score",
            "ml_score",
            "final_score",
            "risk_level",
            "decision",
            "model_status");

    private final ObjectMapper mapper = new ObjectMapper();

    public String summarize(Path jsonl) throws IOException {
        Map<String, Long> decisions = new LinkedHashMap<>();
        Map<String, Long> sinks = new LinkedHashMap<>();
        long events = 0;
        try (Stream<String> lines = Files.lines(jsonl)) {
            for (String line : lines.filter(value -> !value.isBlank()).toList()) {
                JsonNode node = mapper.readTree(line);
                events++;
                increment(decisions, node.path("decision").asText("UNKNOWN"));
                increment(sinks, node.path("sinkType").asText("UNKNOWN"));
            }
        }
        StringBuilder report = new StringBuilder();
        report.append("ProvGuard event report\n");
        report.append("Events: ").append(events).append('\n');
        decisions.forEach((key, value) -> report.append("Decision ").append(key).append(": ").append(value).append('\n'));
        sinks.forEach((key, value) -> report.append("Sink ").append(key).append(": ").append(value).append('\n'));
        return report.toString();
    }

    public void writeCsv(Path jsonl, Path csv) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(csv);
                Stream<String> lines = Files.lines(jsonl, StandardCharsets.UTF_8)) {
            writer.write(String.join(",", COLUMNS));
            writer.newLine();
            for (String line : lines.filter(value -> !value.isBlank()).toList()) {
                JsonNode node = mapper.readTree(line);
                writer.write(COLUMNS.stream().map(column -> csv(node, column)).reduce((a, b) -> a + "," + b).orElse(""));
                writer.newLine();
            }
        }
    }

    private static void increment(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }

    private static String csv(JsonNode node, String column) {
        String field = switch (column) {
            case "event_id" -> "eventId";
            case "sink_type" -> "sinkType";
            case "path_family" -> "pathFamily";
            case "path_signature" -> "pathSignature";
            case "stack_depth" -> "stackDepth";
            case "application_frame_count" -> "applicationFrameCount";
            case "jdk_frame_count" -> "jdkFrameCount";
            case "external_library_frame_count" -> "externalLibraryFrameCount";
            case "unique_package_count" -> "uniquePackageCount";
            case "reflection_detected" -> "reflectionDetected";
            case "argument_length" -> "argumentLength";
            case "keyword_risk_score" -> "keywordRiskScore";
            case "path_frequency" -> "pathFrequency";
            case "class_loader_count" -> "classLoaderCount";
            case "rule_score" -> "ruleScore";
            case "ml_score" -> "mlScore";
            case "final_score" -> "finalScore";
            case "risk_level" -> "riskLevel";
            case "model_status" -> "modelStatus";
            default -> column;
        };
        String value = node.path(field).isMissingNode() || node.path(field).isNull() ? "" : node.path(field).asText();
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
