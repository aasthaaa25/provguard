package com.provguard.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.provguard.core.CallFrame;
import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;
import com.provguard.rules.PathFrequencyRule;
import com.provguard.rules.RiskRule;
import com.provguard.rules.RiskRules;
import com.provguard.rules.RuleEvaluation;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Keeps the documented policy page honest against the real rules module. */
class PolicyCatalogTest {
    @Test
    void everyRuleDefinedInTheRulesModuleIsInTheCatalog() {
        Set<String> moduleIds = PolicyCatalog.rulesModuleIds();
        assertTrue(moduleIds.size() >= 8, "expected the standard rule set, got " + moduleIds);
        assertEquals(Set.of(), PolicyCatalog.uncoveredModuleIds(),
                "catalog is missing rules the module defines");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyCatalogEntryIsTraceableToTheRulesModule() {
        Map<String, Object> described = PolicyCatalog.describe(Map.of());
        for (Map<String, Object> rule : (List<Map<String, Object>>) described.get("rules")) {
            assertEquals(Boolean.TRUE, rule.get("definedInRulesModule"), String.valueOf(rule.get("id")));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void documentedScoreContributionsMatchWhatTheRealRulesProduce() {
        Map<String, Integer> documented = new HashMap<>();
        for (Map<String, Object> rule : (List<Map<String, Object>>) PolicyCatalog.describe(Map.of()).get("rules")) {
            documented.put((String) rule.get("id"), (Integer) rule.get("scoreDelta"));
        }
        PathFrequencyRule frequency = new PathFrequencyRule(Clock.systemUTC(), 5, Duration.ofSeconds(10));
        List<RiskRule> rules = RiskRules.standard(Set.of("known.pkg"), Set.of(), Set.of("ok.Allowed"), Set.of("bad.Denied"), frequency);

        CallFrame app = new CallFrame("app.Main", "main", "Main.java", 1, "UNNAMED_MODULE", "app", true);
        CallFrame lib = new CallFrame("org.lib.Thing", "go", "Thing.java", 1, "UNNAMED_MODULE", "app", false);
        SecurityEvent process = RiskRules.event(SinkType.PROCESS_BUILDER_START).toBuilder()
                .callFrames(List.of(app)).pathFamily("app").suspiciousKeywordCount(2).commandLike(true)
                .reflectionDetected(true).argumentLength(500).specialCharacterCount(30)
                .applicationFrameCount(1).pathFrequency(9).build();
        SecurityEvent libs = RiskRules.event(SinkType.RUNTIME_EXEC).toBuilder()
                .callFrames(List.of(lib, lib, lib)).applicationFrameCount(0).externalLibraryFrameCount(3).build();
        SecurityEvent denied = RiskRules.event(SinkType.DESERIALIZATION_RESOLVE_CLASS).toBuilder().requestedClassName("bad.Denied").build();
        SecurityEvent allowed = RiskRules.event(SinkType.DESERIALIZATION_RESOLVE_CLASS).toBuilder().requestedClassName("ok.Allowed").build();
        SecurityEvent unknown = RiskRules.event(SinkType.DESERIALIZATION_RESOLVE_CLASS).toBuilder().requestedClassName("x.Mystery").build();

        Map<String, Integer> observed = new HashMap<>();
        for (SecurityEvent event : List.of(process, libs, denied, allowed, unknown)) {
            for (RiskRule rule : rules) {
                for (RuleEvaluation evaluation : rule.evaluateAll(event)) {
                    observed.put(evaluation.ruleId(), evaluation.scoreDelta());
                }
            }
        }
        assertTrue(observed.size() >= 10, "probes should exercise most rules, saw " + observed.keySet());
        for (Map.Entry<String, Integer> entry : observed.entrySet()) {
            assertEquals(documented.get(entry.getKey()), entry.getValue(), "score for " + entry.getKey());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void decisionMatrixComesFromTheRealBlockingPolicy() {
        Map<String, Object> described = PolicyCatalog.describe(Map.of());
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) described.get("decisionMatrix");
        assertEquals("BLOCK", matrix.get(0).get("mlHigh"));
        assertEquals("ALLOW", matrix.get(3).get("mlLow"));
        assertEquals("ALERT", matrix.get(3).get("mlHigh"));
        assertEquals(Boolean.TRUE, described.get("readOnly"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyBlockModeStopsCalls() {
        List<Map<String, Object>> modes = (List<Map<String, Object>>) PolicyCatalog.describe(Map.of()).get("modes");
        for (Map<String, Object> mode : modes) {
            assertEquals("BLOCK".equals(mode.get("mode")), mode.get("stopsTheCall"), String.valueOf(mode.get("mode")));
        }
    }
}
