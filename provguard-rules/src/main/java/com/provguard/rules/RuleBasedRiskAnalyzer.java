package com.provguard.rules;

import com.provguard.core.Decision;
import com.provguard.core.RiskLevel;
import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;

import java.util.ArrayList;
import java.util.List;

public final class RuleBasedRiskAnalyzer implements RiskAnalyzer {
    private final List<RiskRule> rules;

    public RuleBasedRiskAnalyzer(List<RiskRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @Override
    public RiskResult analyze(SecurityEvent event) {
        int score = 0;
        List<String> matched = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        for (RiskRule rule : rules) {
            for (RuleEvaluation evaluation : rule.evaluateAll(event)) {
                if (!evaluation.matched()) {
                    continue;
                }
                matched.add(evaluation.ruleId());
                if (evaluation.scoreDelta() > 0) {
                    reasons.add(evaluation.explanation());
                }
                score += evaluation.scoreDelta();
            }
        }
        score = Math.max(0, Math.min(100, score));
        Decision policy = BlockingPolicy.analytical(score, null);
        String explanation = reasons.isEmpty() ? "No deterministic rule matched" : String.join("; ", reasons);
        return new RiskResult(
                score,
                null,
                score,
                RiskLevel.fromScore(score),
                policy,
                policy,
                matched,
                "RULES_ONLY",
                explanation,
                false);
    }
}
