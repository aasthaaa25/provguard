package com.provguard.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maps one JSONL line written by {@code JsonEventMapper} to an {@link EventSummary} or a detail view.
 * The original record is never modified. Malformed input raises {@link MalformedRecord}.
 *
 * <p>Event ids: the agent writes a random UUID in {@code eventId}. When it is missing or not a UUID the bridge
 * derives {@code derived-<16 hex>} from SHA-256 of the raw line, so the same line always gets the same id.
 */
public final class EventParser {
    public static final class MalformedRecord extends Exception {
        private static final long serialVersionUID = 1L;

        public MalformedRecord(String message) {
            super(message);
        }
    }

    static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Set<String> DECISIONS = Set.of("ALLOW", "ALERT", "BLOCK");
    private static final Set<String> LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final int MAX_TEXT = 512;
    private static final int MAX_FRAME_TEXT = 1024;
    private static final int MAX_FRAMES = 200;

    private final ObjectMapper mapper;

    public EventParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode readTree(byte[] line, int length) throws MalformedRecord {
        try {
            JsonNode root = mapper.readTree(line, 0, length);
            if (root == null || !root.isObject()) {
                throw new MalformedRecord("Line is not a JSON object");
            }
            return root;
        } catch (MalformedRecord record) {
            throw record;
        } catch (Exception exception) {
            throw new MalformedRecord("Invalid JSON");
        }
    }

    public EventSummary summarize(JsonNode root, byte[] rawLine, int length, long seq, long offset)
            throws MalformedRecord {
        String sinkType = text(root, "sinkType");
        if (sinkType == null || sinkType.isBlank()) {
            throw new MalformedRecord("Missing sinkType");
        }
        String timestamp = text(root, "timestamp");
        Instant instant;
        try {
            instant = Instant.parse(timestamp == null ? "" : timestamp);
        } catch (DateTimeParseException exception) {
            throw new MalformedRecord("Missing or invalid timestamp");
        }
        String id = idFor(root, rawLine, length);
        boolean derived = id.startsWith("derived-");

        String decision = enumText(root, "decision", DECISIONS);
        String policy = enumText(root, "policyDecision", DECISIONS);
        Boolean enforced = bool(root, "enforced");
        List<String> rules = stringList(root.get("matchedRules"), 24);

        List<String[]> frames = frames(root.get("callFrames"));
        StringBuilder frameText = new StringBuilder();
        String entry = null;
        for (int i = frames.size() - 1; i >= 0; i--) {
            String[] frame = frames.get(i);
            boolean application = "true".equals(frame[2]);
            if (application && entry == null) {
                entry = shortClass(frame[0]) + "." + frame[1];
            }
            if (!platform(frame[0]) && frameText.length() < MAX_FRAME_TEXT) {
                frameText.append(frame[0]).append('.').append(frame[1]).append(' ');
            }
        }
        return new EventSummary(
                seq,
                id,
                derived,
                instant.toEpochMilli(),
                timestamp,
                clean(sinkType, 64),
                clean(text(root, "sinkMethod"), 128),
                clean(text(root, "mode"), 16),
                integer(root, "ruleScore"),
                integer(root, "mlScore"),
                integer(root, "finalScore"),
                enumText(root, "riskLevel", LEVELS),
                decision,
                policy,
                enforced,
                clean(text(root, "modelStatus"), 48),
                rules,
                entry,
                clean(text(root, "pathSignature"), 256),
                clean(text(root, "threadName"), 96),
                root.has("callFrames") && root.get("callFrames").isArray() ? Integer.valueOf(frames.size()) : null,
                bool(root, "queueDropped"),
                EventSummary.outcomeOf(decision, policy, enforced),
                frameText.toString().toLowerCase(java.util.Locale.ROOT),
                offset,
                length);
    }

    /** Builds the detail response for one event from its raw record. Whitelists fields; unknown fields are dropped. */
    public Map<String, Object> detail(JsonNode root, EventSummary summary) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", summary.id());
        out.put("derivedId", summary.derivedId());
        out.put("seq", summary.seq());
        out.put("schemaVersion", integer(root, "schemaVersion"));
        out.put("timestamp", summary.timestamp());
        out.put("ts", summary.ts());
        out.put("mode", summary.mode());
        out.put("outcome", summary.outcome());

        Map<String, Object> sink = new LinkedHashMap<>();
        sink.put("type", summary.sinkType());
        sink.put("method", summary.sinkMethod());
        sink.put("requestedClassName", blankToNull(clean(text(root, "requestedClassName"), 256)));
        out.put("sink", sink);

        Map<String, Object> thread = new LinkedHashMap<>();
        thread.put("name", summary.threadName());
        thread.put("id", number(root, "threadId"));
        out.put("thread", thread);
        // The agent log has no process id, application name, user, or host. Say so explicitly.
        out.put("process", Map.of("recorded", false));

        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("score", summary.ruleScore());
        rules.put("matchedRules", summary.matchedRules());
        rules.put("explanation", clean(text(root, "explanation"), 1024));
        out.put("rules", rules);

        Map<String, Object> ml = new LinkedHashMap<>();
        ml.put("score", summary.mlScore());
        ml.put("status", summary.modelStatus());
        out.put("ml", ml);

        Map<String, Object> combined = new LinkedHashMap<>();
        combined.put("score", summary.finalScore());
        combined.put("riskLevel", summary.riskLevel());
        out.put("combined", combined);

        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("recorded", summary.decision());
        decision.put("policy", summary.policyDecision());
        decision.put("enforced", summary.enforced());
        out.put("decision", decision);

        Map<String, Object> argument = new LinkedHashMap<>();
        argument.put("count", integer(root, "argumentCount"));
        argument.put("type", clean(text(root, "argumentType"), 64));
        argument.put("length", integer(root, "argumentLength"));
        argument.put("sha256", clean(text(root, "argumentHash"), 80));
        argument.put("redactedPreview", clean(text(root, "redactedArgumentPreview"), 64));
        argument.put("suspiciousKeywordCount", integer(root, "suspiciousKeywordCount"));
        argument.put("keywordRiskScore", integer(root, "keywordRiskScore"));
        argument.put("specialCharacterCount", integer(root, "specialCharacterCount"));
        argument.put("urlLike", bool(root, "urlLike"));
        argument.put("commandLike", bool(root, "commandLike"));
        argument.put("nullArgument", bool(root, "nullArgument"));
        out.put("argument", argument);

        List<Map<String, Object>> frames = executionOrderFrames(root, summary);
        Map<String, Object> path = new LinkedHashMap<>();
        path.put("signature", summary.pathSignature());
        path.put("family", clean(text(root, "pathFamily"), 128));
        path.put("stackDepth", integer(root, "stackDepth"));
        path.put("frameCount", frames.size());
        path.put("applicationFrameCount", integer(root, "applicationFrameCount"));
        path.put("jdkFrameCount", integer(root, "jdkFrameCount"));
        path.put("externalLibraryFrameCount", integer(root, "externalLibraryFrameCount"));
        path.put("uniquePackageCount", integer(root, "uniquePackageCount"));
        path.put("classLoaderCount", integer(root, "classLoaderCount"));
        path.put("frequency", integer(root, "pathFrequency"));
        path.put("reflectionDetected", bool(root, "reflectionDetected"));
        path.put("agentFrameLimit", 48);
        path.put("frames", frames);
        out.put("path", path);

        out.put("queueDropped", summary.queueDropped());
        return out;
    }

    /**
     * The agent stores frames innermost first (index 0 is the monitored JDK method, the last element is the
     * outermost frame). The API returns them in execution order: entry first, sink last.
     */
    private List<Map<String, Object>> executionOrderFrames(JsonNode root, EventSummary summary) {
        List<String[]> raw = frames(root.get("callFrames"));
        JsonNode nodes = root.get("callFrames");
        String expectedSink = expectedSinkClass(summary.sinkType());
        boolean reflectionRuleMatched = summary.matchedRules().contains("REFLECTION_IN_CALL_PATH");
        List<Map<String, Object>> out = new ArrayList<>();
        int n = raw.size();
        for (int position = 0; position < n; position++) {
            int arrayIndex = n - 1 - position;
            String[] frame = raw.get(arrayIndex);
            JsonNode node = nodes.get(arrayIndex);
            boolean application = "true".equals(frame[2]);
            String category = application ? "application" : platform(frame[0]) ? "platform" : "external";
            String role = position == 0 ? "entry" : "intermediate";
            if (arrayIndex == 0 && expectedSink != null && expectedSink.equals(frame[0])) {
                role = "sink";
            }
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("position", position + 1);
            json.put("arrayIndex", arrayIndex);
            json.put("className", frame[0]);
            json.put("methodName", frame[1]);
            json.put("fileName", blankToNull(clean(node.path("fileName").asText(""), 128)));
            int line = node.path("lineNumber").asInt(-1);
            json.put("lineNumber", line >= 0 ? Integer.valueOf(line) : null);
            json.put("moduleName", clean(node.path("moduleName").asText(null), 128));
            json.put("classLoaderName", clean(node.path("classLoaderName").asText(null), 128));
            json.put("applicationClass", application);
            json.put("category", category);
            json.put("role", role);
            List<Map<String, String>> flags = new ArrayList<>();
            if (isReflection(frame[0])) {
                // Same class-name test the agent uses (StackTraceCollector.isReflection).
                flags.add(Map.of(
                        "code", "REFLECTION",
                        "reason", "Reflection frame in the recorded stack"
                                + (reflectionRuleMatched ? "; rule REFLECTION_IN_CALL_PATH matched this event" : "")));
            }
            json.put("flags", flags);
            out.add(json);
        }
        return out;
    }

    private static String expectedSinkClass(String sinkType) {
        return switch (sinkType == null ? "" : sinkType) {
            case "RUNTIME_EXEC" -> "java.lang.Runtime";
            case "PROCESS_BUILDER_START" -> "java.lang.ProcessBuilder";
            case "DESERIALIZATION_RESOLVE_CLASS" -> "java.io.ObjectInputStream";
            default -> null;
        };
    }

    static boolean isReflection(String className) {
        return className.startsWith("java.lang.reflect.")
                || className.startsWith("jdk.internal.reflect.")
                || className.contains("MethodAccessor");
    }

    static boolean platform(String className) {
        return className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("com.sun.");
    }

    /** Each entry is {className, methodName, applicationClass}. Frames without a class name are skipped. */
    private static List<String[]> frames(JsonNode node) {
        List<String[]> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        ArrayNode array = (ArrayNode) node;
        for (int i = 0; i < array.size() && out.size() < MAX_FRAMES; i++) {
            JsonNode frame = array.get(i);
            String cls = clean(frame.path("className").asText(null), 256);
            if (cls == null || cls.isBlank()) {
                continue;
            }
            String method = clean(frame.path("methodName").asText(""), 128);
            out.add(new String[] {cls, method == null ? "" : method, String.valueOf(frame.path("applicationClass").asBoolean(false))});
        }
        return out;
    }

    /** The record's own eventId when it is a safe token, otherwise an id derived from the raw line. */
    public static String idFor(JsonNode root, byte[] line, int length) {
        String rawId = text(root, "eventId");
        if (rawId != null && SAFE_ID.matcher(rawId).matches() && !rawId.startsWith("derived-")) {
            return rawId;
        }
        return derivedId(line, length);
    }

    private static String derivedId(byte[] line, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(line, 0, length);
            return "derived-" + HexFormat.of().formatHex(digest.digest(), 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String shortClass(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() || !node.isValueNode() ? null : node.asText();
    }

    private static Integer integer(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isNumber() ? Integer.valueOf(node.asInt()) : null;
    }

    private static Long number(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isNumber() ? Long.valueOf(node.asLong()) : null;
    }

    private static Boolean bool(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null && node.isBoolean() ? Boolean.valueOf(node.asBoolean()) : null;
    }

    private static String enumText(JsonNode root, String field, Set<String> allowed) {
        String value = text(root, field);
        if (value == null) {
            return null;
        }
        String upper = value.trim().toUpperCase(java.util.Locale.ROOT);
        return allowed.contains(upper) ? upper : null;
    }

    private static List<String> stringList(JsonNode node, int max) {
        List<String> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isValueNode() && out.size() < max) {
                    String value = clean(item.asText(), 96);
                    if (value != null && !value.isBlank()) {
                        out.add(value);
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Drops control characters and bounds length. HTML escaping is the browser's job and is done there. */
    static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), max));
        for (int i = 0; i < value.length() && out.length() < max; i++) {
            char c = value.charAt(i);
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
