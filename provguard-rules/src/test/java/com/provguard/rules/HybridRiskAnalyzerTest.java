package com.provguard.rules;

import com.provguard.core.AgentMode;
import com.provguard.core.Decision;
import com.provguard.core.RiskResult;
import com.provguard.core.SinkType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridRiskAnalyzerTest {

    @Test
    void secondCombineDropsTheUnavailableSentence() {
        RiskResult rules = new RuleBasedRiskAnalyzer(RiskRules.standard(
                Set.of("com.provguard.demo.safe"),
                Set.of(),
                Set.of("com.provguard.demo.safe.SafeSerializableObject"),
                Set.of("com.provguard.demo.unsafe.BlockedDemoObject"),
                new PathFrequencyRule(Clock.systemUTC(), 5, Duration.ofSeconds(10))))
                .analyze(RiskRules.event(SinkType.RUNTIME_EXEC));
        HybridRiskAnalyzer hybrid = new HybridRiskAnalyzer();
        RiskResult fast = hybrid.combine(rules, null, "ML_UNAVAILABLE", AgentMode.MONITOR);
        assertTrue(fast.explanation().contains("unavailable"));
        RiskResult logged = hybrid.combine(fast, 100, "AVAILABLE", AgentMode.MONITOR);
        assertFalse(logged.explanation().toLowerCase().contains("unavailable"));
        assertEquals(
                "Anomaly score is high, so the event is alerted and not blocked.",
                logged.explanation());
        assertEquals(Decision.ALERT, logged.decision());
        assertEquals("AVAILABLE", logged.modelStatus());
    }
}
