package com.provguard.web;

import java.util.List;

/**
 * Compact in-memory view of one logged event. The full record stays in the JSONL file and is read on demand.
 * Nullable boxed fields mean "not present in the record", never a default value.
 */
public record EventSummary(
        long seq,
        String id,
        boolean derivedId,
        long ts,
        String timestamp,
        String sinkType,
        String sinkMethod,
        String mode,
        Integer ruleScore,
        Integer mlScore,
        Integer finalScore,
        String riskLevel,
        String decision,
        String policyDecision,
        Boolean enforced,
        String modelStatus,
        List<String> matchedRules,
        String entry,
        String pathSignature,
        String threadName,
        Integer frameCount,
        Boolean queueDropped,
        String outcome,
        String frameText,
        long offset,
        int length) {

    public static final String OUTCOME_BLOCKED = "blocked";
    public static final String OUTCOME_POLICY_BLOCK_NOT_ENFORCED = "policy_block_not_enforced";
    public static final String OUTCOME_FLAGGED = "flagged";
    public static final String OUTCOME_ALLOWED = "allowed";
    public static final String OUTCOME_UNKNOWN = "unknown";

    /** True when the recorded decision is ALERT or BLOCK. This is "flagged", not "blocked". */
    public boolean flagged() {
        return "ALERT".equals(decision) || "BLOCK".equals(decision) || OUTCOME_POLICY_BLOCK_NOT_ENFORCED.equals(outcome);
    }

    /** True only when the agent recorded that it stopped the call. */
    public boolean blocked() {
        return Boolean.TRUE.equals(enforced);
    }

    public static String outcomeOf(String decision, String policyDecision, Boolean enforced) {
        if (Boolean.TRUE.equals(enforced)) {
            return OUTCOME_BLOCKED;
        }
        if ("BLOCK".equals(policyDecision)) {
            return OUTCOME_POLICY_BLOCK_NOT_ENFORCED;
        }
        if ("ALERT".equals(decision) || "BLOCK".equals(decision)) {
            return OUTCOME_FLAGGED;
        }
        if ("ALLOW".equals(decision)) {
            return OUTCOME_ALLOWED;
        }
        return OUTCOME_UNKNOWN;
    }
}
