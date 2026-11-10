package com.provguard.web;

import java.util.UUID;

/** Harmless synthetic records shaped like the agent's JSONL output. */
final class Samples {
    private Samples() {
    }

    static String line(String id, String timestamp, String sink, String decision, String policy, boolean enforced,
                       String mode, int rule, Integer ml, String... rules) {
        String matched = rules.length == 0 ? "" : "\"" + String.join("\",\"", rules) + "\"";
        return "{\"schemaVersion\":1,\"eventId\":" + (id == null ? "null" : "\"" + id + "\"")
                + ",\"timestamp\":\"" + timestamp + "\",\"sinkType\":\"" + sink + "\",\"sinkMethod\":\"ProcessBuilder.start()\","
                + "\"threadName\":\"main\",\"threadId\":1,\"stackDepth\":4,\"pathSignature\":\"App>Helper>ProcessBuilder.start\","
                + "\"pathFamily\":\"demo\",\"argumentCount\":1,\"argumentType\":\"List<String>\",\"argumentLength\":10,"
                + "\"argumentHash\":\"sha256:abc\",\"redactedArgumentPreview\":\"echo hi\",\"suspiciousKeywordCount\":0,"
                + "\"reflectionDetected\":false,\"applicationFrameCount\":2,\"jdkFrameCount\":1,\"queueDropped\":false,"
                + "\"requestedClassName\":\"\",\"callFrames\":["
                + frame("java.lang.ProcessBuilder", "start", "java.base", false) + ","
                + frame("demo.Helper", "run", "UNNAMED_MODULE", true) + ","
                + frame("jdk.internal.reflect.DirectMethodHandleAccessor", "invoke", "java.base", false) + ","
                + frame("demo.App", "main", "UNNAMED_MODULE", true) + "],"
                + "\"ruleScore\":" + rule + (ml == null ? "" : ",\"mlScore\":" + ml) + ",\"finalScore\":" + rule
                + ",\"riskLevel\":\"" + (rule >= 80 ? "CRITICAL" : rule >= 60 ? "HIGH" : rule >= 30 ? "MEDIUM" : "LOW") + "\","
                + "\"decision\":\"" + decision + "\",\"policyDecision\":\"" + policy + "\",\"enforced\":" + enforced
                + ",\"matchedRules\":[" + matched + "],\"modelStatus\":\"" + (ml == null ? "UNAVAILABLE" : "AVAILABLE")
                + "\",\"explanation\":\"x\",\"mode\":\"" + mode + "\"}";
    }

    static String frame(String cls, String method, String module, boolean app) {
        return "{\"className\":\"" + cls + "\",\"methodName\":\"" + method + "\",\"fileName\":\"F.java\",\"lineNumber\":7,"
                + "\"moduleName\":\"" + module + "\",\"classLoaderName\":\"app\",\"applicationClass\":" + app + "}";
    }

    static String allowed() {
        return line(UUID.randomUUID().toString(), "2026-10-24T10:00:00.000000000Z", "PROCESS_BUILDER_START", "ALLOW", "ALLOW", false, "ALERT", 0, 20);
    }
}
