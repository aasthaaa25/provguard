package com.provguard.web;

import com.provguard.core.AgentMode;
import com.provguard.core.Decision;
import com.provguard.core.RiskLevel;
import com.provguard.core.SinkType;
import com.provguard.rules.AbstractRiskRule;
import com.provguard.rules.BlockingPolicy;
import com.provguard.rules.PathFrequencyRule;
import com.provguard.rules.RiskRule;
import com.provguard.rules.RiskRules;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only description of the rules and policy that ship in {@code provguard-rules}.
 *
 * <p>Rule ids and the blocking thresholds are read from the real classes. The rules module exposes no
 * description or score metadata, so those fields are documented here from the source. A unit test evaluates the
 * rule classes to keep the score contributions below honest, and {@link #rulesModuleIds()} marks any entry the
 * rules module no longer defines.
 */
public final class PolicyCatalog {
    private record Entry(String id, String name, String description, List<String> sinks, int scoreDelta,
                         String ruleClass) {
    }

    private static final List<String> PROCESS = List.of("RUNTIME_EXEC", "PROCESS_BUILDER_START");
    private static final List<String> DESER = List.of("DESERIALIZATION_RESOLVE_CLASS");
    private static final List<String> ALL = List.of("RUNTIME_EXEC", "PROCESS_BUILDER_START", "DESERIALIZATION_RESOLVE_CLASS");

    private static final List<Entry> ENTRIES = List.of(
            new Entry("DANGEROUS_COMMAND_KEYWORD", "Dangerous command keyword",
                    "The argument contains at least one risky command keyword from the agent's built-in lexicon.",
                    PROCESS, 35, "KeywordRule"),
            new Entry("MULTIPLE_RISKY_KEYWORDS", "Multiple risky keywords",
                    "Two or more risky keywords were found. Added on top of the single-keyword rule.",
                    PROCESS, 20, "KeywordRule"),
            new Entry("SUSPICIOUS_COMMAND_PATTERN", "Command-shaped argument",
                    "The argument looks like a shell or process command.",
                    PROCESS, 15, "CommandPatternRule"),
            new Entry("LONG_ARGUMENT", "Very long argument",
                    "Argument length is 180 characters or more.",
                    ALL, 10, "LongArgumentRule"),
            new Entry("SPECIAL_CHARACTER_DENSITY", "High special-character count",
                    "The argument holds 8 or more special characters.",
                    ALL, 10, "SpecialCharacterRule"),
            new Entry("REFLECTION_IN_CALL_PATH", "Reflection in call path",
                    "A reflection frame (java.lang.reflect, jdk.internal.reflect, or a MethodAccessor) is in the recorded stack.",
                    ALL, 15, "ReflectionRule"),
            new Entry("UNKNOWN_PATH_SIGNATURE", "Unknown call path",
                    "The path signature is not in the configured known signatures, and the application frames are not all inside the configured known packages.",
                    ALL, 20, "UnknownPathRule"),
            new Entry("REPEATED_PROCESS_EXECUTION", "Repeated process execution",
                    "The same path signature reached a process sink at least the configured number of times (default 5) within 10 seconds.",
                    PROCESS, 25, "PathFrequencyRule"),
            new Entry("UNSAFE_DESERIALIZATION_CLASS", "Denylisted deserialization class",
                    "The class requested during deserialization is on the configured denylist.",
                    DESER, 80, "DeserializationClassRule"),
            new Entry("SAFE_DESERIALIZATION_ALLOWLIST", "Allowlisted deserialization class",
                    "The requested class is on the configured allowlist. Informational, adds no score.",
                    DESER, 0, "DeserializationClassRule"),
            new Entry("UNKNOWN_DESERIALIZATION_CLASS", "Unlisted deserialization class",
                    "The requested class is on neither list and is not a java., javax., or jdk. class.",
                    DESER, 40, "DeserializationClassRule"),
            new Entry("UNKNOWN_EXTERNAL_LIBRARY_RATIO", "External libraries dominate the path",
                    "Frames from non-JDK, non-application code make up at least half of the recorded stack.",
                    ALL, 10, "ExternalLibraryRule"),
            new Entry("NON_APPLICATION_CLASS_LOADER", "No application frame in the path",
                    "No frame in the recorded stack is marked as application code. The rule id mentions class loaders, but the check is on application frames.",
                    ALL, 10, "ClassLoaderRule"));

    private PolicyCatalog() {
    }

    /** Rule ids defined by the real rules module, found by instantiating its standard rule set. */
    public static Set<String> rulesModuleIds() {
        Set<String> ids = new LinkedHashSet<>();
        PathFrequencyRule frequency = new PathFrequencyRule(Clock.systemUTC(), 5, Duration.ofSeconds(10));
        for (RiskRule rule : RiskRules.standard(Set.of(), Set.of(), Set.of(), Set.of(), frequency)) {
            if (rule instanceof AbstractRiskRule abstractRule) {
                ids.add(abstractRule.ruleId());
            }
        }
        return ids;
    }

    /**
     * Rule ids the rules module defines that the catalog does not describe. Empty means nothing is missing.
     * DeserializationClassRule is registered once as DESERIALIZATION_CLASS but emits three ids, which the
     * catalog lists individually.
     */
    public static Set<String> uncoveredModuleIds() {
        Set<String> missing = new LinkedHashSet<>(rulesModuleIds());
        Set<String> catalog = catalogIds();
        missing.removeAll(catalog);
        if (catalog.containsAll(Set.of("UNSAFE_DESERIALIZATION_CLASS", "SAFE_DESERIALIZATION_ALLOWLIST", "UNKNOWN_DESERIALIZATION_CLASS"))) {
            missing.remove("DESERIALIZATION_CLASS");
        }
        return missing;
    }

    public static Set<String> catalogIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Entry entry : ENTRIES) {
            ids.add(entry.id());
        }
        return ids;
    }

    public static Map<String, Object> describe(Map<String, Long> observedMatches) {
        Set<String> moduleIds = rulesModuleIds();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("readOnly", true);
        out.put("readOnlyNote", "Rules are compiled into the agent. Thresholds, packages, and allow/deny lists are set through agent "
                + "arguments when the JVM starts. This dashboard cannot change them.");

        List<Map<String, Object>> rules = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("id", entry.id());
            rule.put("name", entry.name());
            rule.put("description", entry.description());
            rule.put("sinks", entry.sinks());
            rule.put("scoreDelta", entry.scoreDelta());
            rule.put("ruleClass", entry.ruleClass());
            rule.put("enabled", null);
            rule.put("enabledNote", "Always active. The rules module has no enable/disable switch.");
            rule.put("action", "Adds to the rule score. A rule never blocks on its own; the blocking policy decides.");
            rule.put("definedInRulesModule", moduleIds.contains(entry.id()) || derivedFromRuleClass(entry, moduleIds));
            rule.put("observedMatches", observedMatches.getOrDefault(entry.id(), 0L));
            rules.add(rule);
        }
        out.put("rules", rules);

        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("criticalRuleScore", BlockingPolicy.CRITICAL_RULE_SCORE);
        thresholds.put("highRuleScore", BlockingPolicy.HIGH_RULE_SCORE);
        thresholds.put("mediumRuleScore", BlockingPolicy.MEDIUM_RULE_SCORE);
        thresholds.put("highMlScore", BlockingPolicy.HIGH_ML_SCORE);
        out.put("thresholds", thresholds);

        List<Map<String, Object>> levels = new ArrayList<>();
        for (RiskLevel level : RiskLevel.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("level", level.name());
            row.put("minScore", switch (level) {
                case LOW -> 0;
                case MEDIUM -> BlockingPolicy.MEDIUM_RULE_SCORE;
                case HIGH -> BlockingPolicy.HIGH_RULE_SCORE;
                case CRITICAL -> BlockingPolicy.CRITICAL_RULE_SCORE;
            });
            levels.add(row);
        }
        out.put("riskLevels", levels);

        // Each row is produced by calling the real BlockingPolicy, not typed in by hand.
        List<Map<String, Object>> matrix = new ArrayList<>();
        int[][] bands = {{90, 80, 100}, {70, 60, 79}, {45, 30, 59}, {10, 0, 29}};
        for (int[] band : bands) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ruleScoreFrom", band[1]);
            row.put("ruleScoreTo", band[2]);
            row.put("mlHigh", BlockingPolicy.analytical(band[0], 95).name());
            row.put("mlLow", BlockingPolicy.analytical(band[0], 10).name());
            row.put("mlUnavailable", BlockingPolicy.analytical(band[0], null).name());
            matrix.add(row);
        }
        out.put("decisionMatrix", matrix);

        List<Map<String, Object>> modes = new ArrayList<>();
        modes.add(mode(AgentMode.MONITOR, BlockingPolicy.visible(AgentMode.MONITOR, Decision.BLOCK),
                BlockingPolicy.shouldBlock(AgentMode.MONITOR, Decision.BLOCK),
                "Records every event. The recorded decision equals the policy decision, so it can read BLOCK, but the call is never stopped."));
        modes.add(mode(AgentMode.ALERT, BlockingPolicy.visible(AgentMode.ALERT, Decision.BLOCK),
                BlockingPolicy.shouldBlock(AgentMode.ALERT, Decision.BLOCK),
                "Records every event. A policy BLOCK is shown as ALERT in the recorded decision. The call is never stopped."));
        modes.add(mode(AgentMode.BLOCK, BlockingPolicy.visible(AgentMode.BLOCK, Decision.BLOCK),
                BlockingPolicy.shouldBlock(AgentMode.BLOCK, Decision.BLOCK),
                "Throws ProvGuardBlockedException on the application thread when the policy decision is BLOCK."));
        out.put("modes", modes);

        List<Map<String, Object>> sinks = new ArrayList<>();
        for (SinkType sink : SinkType.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", sink.name());
            row.put("monitored", sink.mandatory());
            sinks.add(row);
        }
        out.put("sinks", sinks);
        out.put("agentArguments", agentArguments());
        out.put("agentArgumentsNote", "Defaults are taken from AgentConfig in provguard-agent. The values a running agent was "
                + "started with are not written to the event log, so this page cannot show them.");
        return out;
    }

    /** Rules such as MULTIPLE_RISKY_KEYWORDS are emitted by a rule class whose own id is a sibling. */
    private static boolean derivedFromRuleClass(Entry entry, Set<String> moduleIds) {
        return switch (entry.id()) {
            case "MULTIPLE_RISKY_KEYWORDS" -> moduleIds.contains("DANGEROUS_COMMAND_KEYWORD");
            case "UNSAFE_DESERIALIZATION_CLASS", "SAFE_DESERIALIZATION_ALLOWLIST", "UNKNOWN_DESERIALIZATION_CLASS" ->
                    moduleIds.contains("DESERIALIZATION_CLASS");
            default -> false;
        };
    }

    private static Map<String, Object> mode(AgentMode mode, Decision recorded, boolean enforces, String description) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("mode", mode.name());
        row.put("recordedDecisionForPolicyBlock", recorded.name());
        row.put("stopsTheCall", enforces);
        row.put("description", description);
        return row;
    }

    private static List<Map<String, Object>> agentArguments() {
        String[][] args = {
                {"mode", "MONITOR", "MONITOR, ALERT, or BLOCK."},
                {"events", "events.jsonl", "Path of the JSONL log the agent appends to."},
                {"queueCapacity", "1024", "Bounded queue between the application thread and the background worker."},
                {"mlUrl", "disabled", "Optional HTTP scoring endpoint. The packaged Java model is the fallback."},
                {"knownPackages", "com.provguard.demo.safe", "Application packages treated as a known path."},
                {"appPackages", "com.provguard.demo", "Package prefixes treated as application code."},
                {"knownSignatures", "none", "Path signatures treated as known."},
                {"allowlist", "com.provguard.demo.safe.SafeSerializableObject", "Classes allowed during deserialization."},
                {"denylist", "com.provguard.demo.unsafe.BlockedDemoObject", "Classes denied during deserialization."},
                {"frequencyThreshold", "5", "Repeated-execution threshold inside a 10 second window."},
                {"verbose", "true", "Print agent activity to the console."}};
        List<Map<String, Object>> out = new ArrayList<>();
        for (String[] arg : args) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", arg[0]);
            row.put("default", arg[1]);
            row.put("description", arg[2]);
            out.add(row);
        }
        return out;
    }
}
