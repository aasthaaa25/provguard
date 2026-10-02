package com.provguard.core;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of rule analysis combined with an optional anomaly score.
 * {@code decision} is the decision recorded for the active mode.
 * {@code enforced} is true only when Block mode actually stops the call.
 */
public final class RiskResult {
    private final int ruleScore;
    private final Integer mlScore;
    private final int finalScore;
    private final RiskLevel riskLevel;
    private final Decision decision;
    private final Decision policyDecision;
    private final List<String> matchedRules;
    private final String modelStatus;
    private final String explanation;
    private final boolean enforced;

    public RiskResult(
            int ruleScore,
            Integer mlScore,
            int finalScore,
            RiskLevel riskLevel,
            Decision decision,
            Decision policyDecision,
            List<String> matchedRules,
            String modelStatus,
            String explanation,
            boolean enforced) {
        this.ruleScore = ruleScore;
        this.mlScore = mlScore;
        this.finalScore = finalScore;
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.policyDecision = Objects.requireNonNull(policyDecision, "policyDecision");
        this.matchedRules = List.copyOf(matchedRules == null ? List.of() : matchedRules);
        this.modelStatus = modelStatus == null ? "RULES_ONLY" : modelStatus;
        this.explanation = explanation == null ? "" : explanation;
        this.enforced = enforced;
    }

    public int ruleScore() {
        return ruleScore;
    }

    public Integer mlScore() {
        return mlScore;
    }

    public int finalScore() {
        return finalScore;
    }

    public RiskLevel riskLevel() {
        return riskLevel;
    }

    public Decision decision() {
        return decision;
    }

    public Decision policyDecision() {
        return policyDecision;
    }

    public List<String> matchedRules() {
        return matchedRules;
    }

    public String modelStatus() {
        return modelStatus;
    }

    public String explanation() {
        return explanation;
    }

    public boolean enforced() {
        return enforced;
    }

    public RiskResult withEnforced(boolean enforced) {
        return new RiskResult(
                ruleScore,
                mlScore,
                finalScore,
                riskLevel,
                decision,
                policyDecision,
                matchedRules,
                modelStatus,
                explanation,
                enforced);
    }
}
