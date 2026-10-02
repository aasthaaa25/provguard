package com.provguard.rules;

import com.provguard.core.SecurityEvent;

import java.util.ArrayList;
import java.util.List;

public final class KeywordRule extends AbstractRiskRule {
    public KeywordRule() {
        super("DANGEROUS_COMMAND_KEYWORD");
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        List<RuleEvaluation> matches = evaluateAll(event);
        return matches.isEmpty() ? unmatched() : matches.get(0);
    }

    @Override
    public List<RuleEvaluation> evaluateAll(SecurityEvent event) {
        if (event.suspiciousKeywordCount() <= 0) {
            return List.of();
        }
        List<RuleEvaluation> matches = new ArrayList<>();
        matches.add(matched(35, "Suspicious command keyword found"));
        if (event.suspiciousKeywordCount() >= 2) {
            matches.add(RuleEvaluation.match("MULTIPLE_RISKY_KEYWORDS", 20, "Multiple risky keywords found"));
        }
        return matches;
    }
}
