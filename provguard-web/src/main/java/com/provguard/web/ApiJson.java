package com.provguard.web;

import java.util.LinkedHashMap;
import java.util.Map;

/** API-facing projection of an {@link EventSummary}. Internal fields (offsets, search text) are not exposed. */
final class ApiJson {
    private ApiJson() {
    }

    static Map<String, Object> summary(EventSummary e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seq", e.seq());
        out.put("id", e.id());
        out.put("derivedId", e.derivedId());
        out.put("ts", e.ts());
        out.put("timestamp", e.timestamp());
        out.put("sinkType", e.sinkType());
        out.put("sinkMethod", e.sinkMethod());
        out.put("mode", e.mode());
        out.put("ruleScore", e.ruleScore());
        out.put("mlScore", e.mlScore());
        out.put("finalScore", e.finalScore());
        out.put("riskLevel", e.riskLevel());
        out.put("decision", e.decision());
        out.put("policyDecision", e.policyDecision());
        out.put("enforced", e.enforced());
        out.put("outcome", e.outcome());
        out.put("modelStatus", e.modelStatus());
        out.put("matchedRules", e.matchedRules());
        out.put("entry", e.entry());
        out.put("pathSignature", e.pathSignature());
        out.put("threadName", e.threadName());
        out.put("frameCount", e.frameCount());
        out.put("queueDropped", e.queueDropped());
        return out;
    }
}
