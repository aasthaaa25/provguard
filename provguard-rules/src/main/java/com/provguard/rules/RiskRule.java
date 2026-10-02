package com.provguard.rules;

import com.provguard.core.SecurityEvent;

import java.util.List;

public interface RiskRule {
    RuleEvaluation evaluate(SecurityEvent event);

    default List<RuleEvaluation> evaluateAll(SecurityEvent event) {
        RuleEvaluation evaluation = evaluate(event);
        return evaluation.matched() ? List.of(evaluation) : List.of();
    }
}
