package com.provguard.rules;

public record RuleEvaluation(String ruleId, int scoreDelta, boolean matched, String explanation) {
    public static RuleEvaluation unmatched() {
        return new RuleEvaluation("", 0, false, "");
    }

    public static RuleEvaluation match(String ruleId, int scoreDelta, String explanation) {
        return new RuleEvaluation(ruleId, scoreDelta, true, explanation);
    }
}
