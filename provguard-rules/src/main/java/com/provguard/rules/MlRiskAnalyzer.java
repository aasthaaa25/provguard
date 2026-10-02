package com.provguard.rules;

import com.provguard.core.AgentMode;
import com.provguard.core.Decision;
import com.provguard.core.RiskLevel;
import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;

import java.util.List;

/**
 * Turns an anomaly score into a risk result.
 * The decision is alert or allow. This analyzer never selects block by itself.
 */
public final class MlRiskAnalyzer implements RiskAnalyzer {
    private final AnomalyScoreSource source;

    public MlRiskAnalyzer(AnomalyScoreSource source) {
        this.source = source;
    }

    @Override
    public RiskResult analyze(SecurityEvent event) {
        boolean available = source.available();
        int score = available ? source.score(event) : 0;
        Decision policy = available && score >= BlockingPolicy.HIGH_ML_SCORE ? Decision.ALERT : Decision.ALLOW;
        return new RiskResult(
                0,
                available ? score : null,
                available ? score : 0,
                RiskLevel.fromScore(available ? score : 0),
                policy,
                policy,
                List.of(),
                available ? "AVAILABLE" : "ML_UNAVAILABLE",
                available ? "Anomaly score from the configured model" : "Anomaly model is unavailable",
                false);
    }

    public interface AnomalyScoreSource {
        boolean available();

        int score(SecurityEvent event);
    }
}
