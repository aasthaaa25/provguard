package com.provguard.rules;

import com.provguard.core.AgentMode;
import com.provguard.core.Decision;

/**
 * Deterministic blocking policy.
 * A high anomaly score never blocks unless a rule score is already in the high band.
 * Rule scores from 60 to 79 alert when anomaly confirmation is absent.
 */
public final class BlockingPolicy {
    public static final int CRITICAL_RULE_SCORE = 80;
    public static final int HIGH_RULE_SCORE = 60;
    public static final int MEDIUM_RULE_SCORE = 30;
    public static final int HIGH_ML_SCORE = 80;

    private BlockingPolicy() {
    }

    public static Decision analytical(int ruleScore, Integer mlScore) {
        int boundedRule = Math.max(0, Math.min(100, ruleScore));
        int ml = mlScore == null ? -1 : mlScore;
        if (boundedRule >= CRITICAL_RULE_SCORE) {
            return Decision.BLOCK;
        }
        if (boundedRule >= HIGH_RULE_SCORE && ml >= HIGH_ML_SCORE) {
            return Decision.BLOCK;
        }
        if (boundedRule >= HIGH_RULE_SCORE || boundedRule >= MEDIUM_RULE_SCORE) {
            return Decision.ALERT;
        }
        if (ml >= HIGH_ML_SCORE) {
            return Decision.ALERT;
        }
        return Decision.ALLOW;
    }

    public static Decision visible(AgentMode mode, Decision analytical) {
        if (mode == AgentMode.ALERT && analytical == Decision.BLOCK) {
            return Decision.ALERT;
        }
        return analytical;
    }

    public static boolean shouldBlock(AgentMode mode, Decision analytical) {
        return mode == AgentMode.BLOCK && analytical == Decision.BLOCK;
    }

    public static int finalScore(int ruleScore, Integer mlScore, boolean mlAvailable) {
        int boundedRule = Math.max(0, Math.min(100, ruleScore));
        if (!mlAvailable || mlScore == null) {
            return boundedRule;
        }
        int blended = (int) Math.round(boundedRule * 0.7 + mlScore * 0.3);
        return Math.max(0, Math.min(100, Math.max(boundedRule, blended)));
    }
}
