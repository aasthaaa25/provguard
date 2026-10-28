package com.provguard.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.provguard.core.AgentMode;
import com.provguard.core.Decision;
import com.provguard.core.RiskLevel;
import com.provguard.rules.BlockingPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Synthetic events for UI previews. Only used with {@code --demo}; the dashboard labels every screen as demo data.
 * Decisions are computed with the real {@link BlockingPolicy}, so the sample records are internally consistent.
 * The classes, commands, and scores are invented and do not describe any real application or detection result.
 */
final class DemoData {
    private record Template(String sinkType, String sinkMethod, String command, String requestedClass, int ruleScore,
                            List<String> rules, String[][] appFrames, boolean reflection) {
    }

    private static final String[][] REPORT_PATH = {
            {"demo.shop.Main", "main"},
            {"demo.shop.web.OrderController", "handle"},
            {"demo.shop.report.ReportService", "render"},
            {"demo.shop.util.ShellHelper", "run"}};
    private static final String[][] IMPORT_PATH = {
            {"demo.shop.Main", "main"},
            {"demo.shop.web.ImportController", "upload"},
            {"demo.shop.io.PayloadReader", "read"}};
    private static final String[][] SCRIPT_PATH = {
            {"demo.shop.Main", "main"},
            {"demo.shop.jobs.NightlyJob", "run"},
            {"demo.shop.util.ShellHelper", "run"}};

    private static final List<Template> TEMPLATES = List.of(
            new Template("PROCESS_BUILDER_START", "ProcessBuilder.start()", "echo demo-report-ready", "", 0,
                    List.of(), REPORT_PATH, false),
            new Template("RUNTIME_EXEC", "Runtime.exec", "echo demo-nightly-check", "", 20,
                    List.of("UNKNOWN_PATH_SIGNATURE"), SCRIPT_PATH, false),
            new Template("DESERIALIZATION_RESOLVE_CLASS", "ObjectInputStream.resolveClass(ObjectStreamClass)", "",
                    "demo.shop.model.Order", 0, List.of("SAFE_DESERIALIZATION_ALLOWLIST"), IMPORT_PATH, false),
            new Template("PROCESS_BUILDER_START", "ProcessBuilder.start()", "echo curl http://example.invalid/x", "", 55,
                    List.of("DANGEROUS_COMMAND_KEYWORD", "UNKNOWN_PATH_SIGNATURE"), SCRIPT_PATH, false),
            new Template("DESERIALIZATION_RESOLVE_CLASS", "ObjectInputStream.resolveClass(ObjectStreamClass)", "",
                    "demo.vendor.Widget", 60, List.of("UNKNOWN_DESERIALIZATION_CLASS", "UNKNOWN_PATH_SIGNATURE"),
                    IMPORT_PATH, false),
            new Template("PROCESS_BUILDER_START", "ProcessBuilder.start()", "echo powershell -enc curl", "", 90,
                    List.of("DANGEROUS_COMMAND_KEYWORD", "MULTIPLE_RISKY_KEYWORDS", "SUSPICIOUS_COMMAND_PATTERN",
                            "UNKNOWN_PATH_SIGNATURE"), SCRIPT_PATH, false),
            new Template("RUNTIME_EXEC", "Runtime.exec", "echo curl http://example.invalid/y", "", 70,
                    List.of("DANGEROUS_COMMAND_KEYWORD", "REFLECTION_IN_CALL_PATH", "UNKNOWN_PATH_SIGNATURE"),
                    REPORT_PATH, true),
            new Template("DESERIALIZATION_RESOLVE_CLASS", "ObjectInputStream.resolveClass(ObjectStreamClass)", "",
                    "demo.shop.unsafe.GadgetStub", 80, List.of("UNSAFE_DESERIALIZATION_CLASS"), IMPORT_PATH, false));

    private static final int[] WEIGHTS = {26, 14, 14, 8, 6, 4, 4, 3};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DemoData() {
    }

    /** About 70 events over the last three hours: a MONITOR session, an ALERT session, then a BLOCK session. */
    static List<String> generate(long nowMs) {
        Random random = new Random(7);
        List<String> lines = new ArrayList<>();
        int total = 72;
        long start = nowMs - 3L * 3600_000L;
        for (int i = 0; i < total; i++) {
            long ts = start + (long) ((i + random.nextDouble()) * (3.0 * 3600_000L / total));
            AgentMode mode = i < 38 ? AgentMode.MONITOR : i < 62 ? AgentMode.ALERT : AgentMode.BLOCK;
            lines.add(event(random, ts, mode));
        }
        return lines;
    }

    static String event(Random random, long tsMs, AgentMode mode) {
        int roll = random.nextInt(java.util.Arrays.stream(WEIGHTS).sum());
        int index = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            roll -= WEIGHTS[i];
            if (roll < 0) {
                index = i;
                break;
            }
        }
        Template t = TEMPLATES.get(index);
        int ml = Math.max(0, Math.min(100, t.ruleScore() / 2 + 15 + random.nextInt(45)));
        if (t.ruleScore() >= 60) {
            ml = 70 + random.nextInt(30);
        }
        Decision analytical = BlockingPolicy.analytical(t.ruleScore(), ml);
        Decision visible = BlockingPolicy.visible(mode, analytical);
        boolean enforced = BlockingPolicy.shouldBlock(mode, analytical);
        int finalScore = BlockingPolicy.finalScore(t.ruleScore(), ml, true);

        List<Map<String, Object>> frames = new ArrayList<>();
        String expected = switch (t.sinkType()) {
            case "RUNTIME_EXEC" -> "java.lang.Runtime";
            case "PROCESS_BUILDER_START" -> "java.lang.ProcessBuilder";
            default -> "java.io.ObjectInputStream";
        };
        String sinkName = switch (t.sinkType()) {
            case "RUNTIME_EXEC" -> "exec";
            case "PROCESS_BUILDER_START" -> "start";
            default -> "resolveClass";
        };
        frames.add(frame(expected, sinkName, expected.substring(expected.lastIndexOf('.') + 1) + ".java", 1000 + random.nextInt(200), "java.base", "bootstrap", false));
        if (t.reflection()) {
            frames.add(frame("jdk.internal.reflect.DirectMethodHandleAccessor", "invoke", "DirectMethodHandleAccessor.java", 103, "java.base", "bootstrap", false));
            frames.add(frame("java.lang.reflect.Method", "invoke", "Method.java", 580, "java.base", "bootstrap", false));
        }
        for (int i = t.appFrames().length - 1; i >= 0; i--) {
            String[] f = t.appFrames()[i];
            frames.add(frame(f[0], f[1], f[0].substring(f[0].lastIndexOf('.') + 1) + ".java", 10 + random.nextInt(90), "UNNAMED_MODULE", "app", true));
        }

        StringBuilder signature = new StringBuilder();
        for (String[] f : t.appFrames()) {
            signature.append(f[0].substring(f[0].lastIndexOf('.') + 1)).append('>');
        }
        signature.append(expected.substring(expected.lastIndexOf('.') + 1)).append('.').append(sinkName);

        String argument = t.requestedClass().isEmpty() ? t.command() : t.requestedClass();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schemaVersion", 1);
        json.put("demo", true);
        json.put("eventId", new UUID(random.nextLong(), random.nextLong()).toString());
        json.put("timestamp", Instant.ofEpochMilli(tsMs).toString());
        json.put("sinkType", t.sinkType());
        json.put("sinkMethod", t.sinkMethod());
        json.put("threadName", "demo-worker-" + (1 + random.nextInt(3)));
        json.put("threadId", 20 + random.nextInt(3));
        json.put("stackDepth", frames.size());
        json.put("pathSignature", signature.toString());
        json.put("pathFamily", "demo.shop");
        json.put("argumentCount", 1);
        json.put("argumentType", t.requestedClass().isEmpty() ? "List<String>" : "ObjectStreamClass");
        json.put("argumentLength", argument.length());
        json.put("argumentHash", "sha256:" + sha(argument));
        json.put("redactedArgumentPreview", argument.length() <= 20 ? argument : argument.substring(0, 20) + "...");
        json.put("suspiciousKeywordCount", t.rules().contains("MULTIPLE_RISKY_KEYWORDS") ? 2 : t.rules().contains("DANGEROUS_COMMAND_KEYWORD") ? 1 : 0);
        json.put("keywordRiskScore", 0);
        json.put("specialCharacterCount", 0);
        json.put("urlLike", argument.contains("http"));
        json.put("commandLike", t.rules().contains("SUSPICIOUS_COMMAND_PATTERN"));
        json.put("nullArgument", false);
        json.put("reflectionDetected", t.reflection());
        json.put("applicationFrameCount", t.appFrames().length);
        json.put("jdkFrameCount", frames.size() - t.appFrames().length);
        json.put("externalLibraryFrameCount", 0);
        json.put("uniquePackageCount", 4);
        json.put("classLoaderCount", 2);
        json.put("pathFrequency", 1);
        json.put("queueDropped", false);
        json.put("requestedClassName", t.requestedClass());
        json.put("callFrames", frames);
        json.put("ruleScore", t.ruleScore());
        json.put("mlScore", ml);
        json.put("finalScore", finalScore);
        json.put("riskLevel", RiskLevel.fromScore(finalScore).name());
        json.put("decision", visible.name());
        json.put("policyDecision", analytical.name());
        json.put("enforced", enforced);
        json.put("matchedRules", t.rules());
        json.put("modelStatus", "AVAILABLE");
        json.put("explanation", t.rules().isEmpty() ? "No deterministic rule matched" : "Synthetic demo record");
        json.put("mode", mode.name());
        try {
            return MAPPER.writeValueAsString(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<String, Object> frame(String cls, String method, String file, int line, String module,
                                              String loader, boolean app) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("className", cls);
        f.put("methodName", method);
        f.put("fileName", file);
        f.put("lineNumber", line);
        f.put("moduleName", module);
        f.put("classLoaderName", loader);
        f.put("applicationClass", app);
        return f;
    }

    private static String sha(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
