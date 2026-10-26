package com.provguard.rules;

import com.provguard.core.SecurityEvent;

public abstract class AbstractRiskRule implements RiskRule {
    private final String ruleId;

    protected AbstractRiskRule(String ruleId) {
        this.ruleId = ruleId;
    }

    public final String ruleId() {
        return ruleId;
    }

    protected final RuleEvaluation matched(int scoreDelta, String explanation) {
        return RuleEvaluation.match(ruleId, scoreDelta, explanation);
    }

    protected final RuleEvaluation unmatched() {
        return RuleEvaluation.unmatched();
    }

    @Override
    public abstract RuleEvaluation evaluate(SecurityEvent event);
}
