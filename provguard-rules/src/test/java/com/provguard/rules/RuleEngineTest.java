package com.provguard.rules;

import com.provguard.core.Decision;
import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

    @Test
    void keywordAndCommandRulesScoreASuspiciousHarmlessPattern() {
        SecurityEvent event = RiskRules.event(SinkType.PROCESS_BUILDER_START).toBuilder()
                .pathFamily("com.provguard.demo.suspicious")
                .callFrames(ListOf.suspicious())
                .suspiciousKeywordCount(2)
                .keywordRiskScore(55)
                .commandLike(true)
                .reflectionDetected(true)
                .applicationFrameCount(1)
                .pathFrequency(1)
                .build();

        RiskResult result = analyzer().analyze(event);
        assertTrue(result.matchedRules().contains("DANGEROUS_COMMAND_KEYWORD"));
        assertTrue(result.matchedRules().contains("MULTIPLE_RISKY_KEYWORDS"));
        assertTrue(result.matchedRules().contains("SUSPICIOUS_COMMAND_PATTERN"));
        assertTrue(result.matchedRules().contains("REFLECTION_IN_CALL_PATH"));
        assertTrue(result.matchedRules().contains("UNKNOWN_PATH_SIGNATURE"));
        assertEquals(100, result.ruleScore());
        assertEquals(Decision.BLOCK, result.policyDecision());
    }

    @Test
    void knownSafePathStaysLow() {
        SecurityEvent event = RiskRules.event(SinkType.RUNTIME_EXEC).toBuilder()
                .argumentLength(12)
                .build();
        RiskResult result = analyzer().analyze(event);
        assertEquals(0, result.ruleScore());
        assertEquals(Decision.ALLOW, result.policyDecision());
    }

    @Test
    void denylistClassIsCriticalAndAllowlistIsInformational() {
        RuleBasedRiskAnalyzer rules = analyzer();
        SecurityEvent blocked = RiskRules.event(SinkType.DESERIALIZATION_RESOLVE_CLASS).toBuilder()
                .requestedClassName("com.provguard.demo.unsafe.BlockedDemoObject")
                .callFrames(ListOf.unsafe())
                .pathFamily("com.provguard.demo.unsafe")
                .build();
        SecurityEvent allowed = RiskRules.event(SinkType.DESERIALIZATION_RESOLVE_CLASS).toBuilder()
                .requestedClassName("com.provguard.demo.safe.SafeSerializableObject")
                .build();

        assertEquals(100, rules.analyze(blocked).ruleScore());
        assertTrue(rules.analyze(blocked).matchedRules().contains("UNSAFE_DESERIALIZATION_CLASS"));
        RiskResult safe = rules.analyze(allowed);
        assertTrue(safe.matchedRules().contains("SAFE_DESERIALIZATION_ALLOWLIST"));
        assertEquals(0, safe.ruleScore());
    }

    @Test
    void unknownClassAlertsAndFrequencyRuleFiresInsideTheWindow() {
        MutableClock clock = new MutableClock();
        PathFrequencyRule frequency = new PathFrequencyRule(clock, 5, Duration.ofSeconds(10));
        RuleBasedRiskAnalyzer rules = new RuleBasedRiskAnalyzer(RiskRules.standard(
                Set.of("com.provguard.demo.safe"),
                Set.of(),
                Set.of("com.provguard.demo.safe.SafeSerializableObject"),
                Set.of("com.provguard.demo.unsafe.BlockedDemoObject"),
                frequency));

        SecurityEvent unknown = RiskRules.event(SinkType.DESERIALIZATION_RESOLVE_CLASS).toBuilder()
                .requestedClassName("com.example.Mystery")
                .callFrames(ListOf.suspicious())
                .build();
        assertTrue(rules.analyze(unknown).matchedRules().contains("UNKNOWN_DESERIALIZATION_CLASS"));
        assertEquals(Decision.ALERT, rules.analyze(unknown).policyDecision());

        SecurityEvent repeated = RiskRules.event(SinkType.RUNTIME_EXEC);
        for (int i = 1; i <= 4; i++) {
            int count = frequency.observe(SinkType.RUNTIME_EXEC, repeated.pathSignature());
            assertTrue(rules.analyze(repeated.toBuilder().pathFrequency(count).build()).ruleScore() < 25
                    || count < 5);
        }
        int fifth = frequency.observe(SinkType.RUNTIME_EXEC, repeated.pathSignature());
        RiskResult result = rules.analyze(repeated.toBuilder().pathFrequency(fifth).build());
        assertTrue(result.matchedRules().contains("REPEATED_PROCESS_EXECUTION"));
        assertEquals(25, result.ruleScore());
    }

    @Test
    void longArgumentAndExternalRatioAddTheirScores() {
        SecurityEvent event = RiskRules.event(SinkType.RUNTIME_EXEC).toBuilder()
                .argumentLength(200)
                .specialCharacterCount(9)
                .applicationFrameCount(1)
                .jdkFrameCount(1)
                .externalLibraryFrameCount(4)
                .stackDepth(6)
                .build();
        RiskResult result = analyzer().analyze(event);
        assertTrue(result.matchedRules().contains("LONG_ARGUMENT"));
        assertTrue(result.matchedRules().contains("SPECIAL_CHARACTER_DENSITY"));
        assertTrue(result.matchedRules().contains("UNKNOWN_EXTERNAL_LIBRARY_RATIO"));
    }

    private static RuleBasedRiskAnalyzer analyzer() {
        return new RuleBasedRiskAnalyzer(RiskRules.standard(
                Set.of("com.provguard.demo.safe"),
                Set.of(),
                Set.of("com.provguard.demo.safe.SafeSerializableObject"),
                Set.of("com.provguard.demo.unsafe.BlockedDemoObject"),
                new PathFrequencyRule(Clock.systemUTC(), 5, Duration.ofSeconds(10))));
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
