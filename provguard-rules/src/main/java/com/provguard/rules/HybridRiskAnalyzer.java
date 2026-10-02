package com.provguard.rules;

import com.provguard.core.AgentMode;
import com.provguard.core.Decision;
import com.provguard.core.RiskLevel;
import com.provguard.core.RiskResult;

/**
 * Strategy that keeps the rule score in charge and treats the anomaly score as supporting evidence.
 */
public final class HybridRiskAnalyzer {
    public RiskResult combine(RiskResult rules, Integer mlScore, String modelStatus, AgentMode mode) {
        boolean available = "AVAILABLE".equals(modelStatus) && mlScore != null;
        Decision analytical = BlockingPolicy.analytical(rules.ruleScore(), available ? mlScore : null);
        Decision visible = BlockingPolicy.visible(mode, analytical);
        boolean enforced = BlockingPolicy.shouldBlock(mode, analytical);
        int finalScore = BlockingPolicy.finalScore(rules.ruleScore(), mlScore, available);
        String explanation = rules.explanation();
        if (!available && !explanation.contains("ML scoring is unavailable")) {
            explanation = explanation + " ML scoring is unavailable; rules decide.";
        } else if (analytical == Decision.ALERT && rules.ruleScore() < BlockingPolicy.MEDIUM_RULE_SCORE) {
            explanation = explanation + " Anomaly score is high, so the event is alerted and not blocked.";
        }
        return new RiskResult(
                rules.ruleScore(),
                available ? mlScore : null,
                finalScore,
                RiskLevel.fromScore(finalScore),
                visible,
                analytical,
                rules.matchedRules(),
                available ? "AVAILABLE" : modelStatus,
                explanation.trim(),
                enforced);
    }
}
