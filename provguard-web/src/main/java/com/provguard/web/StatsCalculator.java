package com.provguard.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregates statistics from indexed records only. Nothing here is estimated or preset. */
public final class StatsCalculator {
    private static final long[] BUCKET_SECONDS = {10, 30, 60, 300, 900, 3600, 21_600, 86_400};

    private StatsCalculator() {
    }

    public static Map<String, Object> compute(List<EventSummary> events, EventLogIndex.Status log, Long bucketOverride) {
        long total = events.size();
        long flagged = 0;
        long blocked = 0;
        long notEnforced = 0;
        long queueDropped = 0;
        Map<String, Long> byDecision = new LinkedHashMap<>();
        Map<String, Long> byOutcome = new LinkedHashMap<>();
        Map<String, Long> bySink = new TreeMap<>();
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        Map<String, Long> byMode = new TreeMap<>();
        Map<String, Long> ruleMatches = new TreeMap<>();
        for (String key : new String[] {"ALLOW", "ALERT", "BLOCK", "UNKNOWN"}) {
            byDecision.put(key, 0L);
        }
        for (String key : new String[] {"allowed", "flagged", "policy_block_not_enforced", "blocked", "unknown"}) {
            byOutcome.put(key, 0L);
        }
        for (String key : new String[] {"LOW", "MEDIUM", "HIGH", "CRITICAL", "UNKNOWN"}) {
            bySeverity.put(key, 0L);
        }
        long mlAvailable = 0;
        long mlMissing = 0;
        long mlHigh = 0;
        long mlSum = 0;
        long[] mlHistogram = new long[10];
        long[] ruleHistogram = new long[10];
        long ruleScored = 0;
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;

        for (EventSummary e : events) {
            if (e.flagged()) {
                flagged++;
            }
            if (e.blocked()) {
                blocked++;
            }
            if (EventSummary.OUTCOME_POLICY_BLOCK_NOT_ENFORCED.equals(e.outcome())) {
                notEnforced++;
            }
            if (Boolean.TRUE.equals(e.queueDropped())) {
                queueDropped++;
            }
            bump(byDecision, e.decision() == null ? "UNKNOWN" : e.decision());
            bump(byOutcome, e.outcome());
            bump(bySink, e.sinkType() == null ? "UNKNOWN" : e.sinkType());
            bump(bySeverity, e.riskLevel() == null ? "UNKNOWN" : e.riskLevel());
            bump(byMode, e.mode() == null ? "UNKNOWN" : e.mode());
            for (String rule : e.matchedRules()) {
                bump(ruleMatches, rule);
            }
            if (e.mlScore() != null && "AVAILABLE".equals(e.modelStatus())) {
                mlAvailable++;
                mlSum += e.mlScore();
                mlHistogram[bin(e.mlScore())]++;
                if (e.mlScore() >= 80) {
                    mlHigh++;
                }
            } else {
                mlMissing++;
            }
            if (e.ruleScore() != null) {
                ruleScored++;
                ruleHistogram[bin(e.ruleScore())]++;
            }
            first = Math.min(first, e.ts());
            last = Math.max(last, e.ts());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("flagged", flagged);
        out.put("blocked", blocked);
        out.put("policyBlockNotEnforced", notEnforced);
        out.put("queueDropped", queueDropped);
        out.put("byDecision", byDecision);
        out.put("byOutcome", byOutcome);
        out.put("bySink", sortedDesc(bySink));
        out.put("bySeverity", bySeverity);
        out.put("byMode", byMode);
        out.put("ruleMatches", sortedDesc(ruleMatches));
        Map<String, Object> ml = new LinkedHashMap<>();
        ml.put("scored", mlAvailable);
        ml.put("unavailable", mlMissing);
        ml.put("mean", mlAvailable == 0 ? null : Math.round(mlSum * 10.0 / mlAvailable) / 10.0);
        ml.put("highCount", mlHigh);
        ml.put("highThreshold", 80);
        ml.put("histogram", histogram(mlHistogram));
        out.put("ml", ml);
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("scored", ruleScored);
        rules.put("histogram", histogram(ruleHistogram));
        out.put("rules", rules);
        out.put("firstEventTs", total == 0 ? null : first);
        out.put("lastEventTs", total == 0 ? null : last);
        out.put("timeline", timeline(events, first, last, bucketOverride));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("indexedEvents", log.indexedEvents());
        source.put("evictedFromIndex", log.evictedEvents());
        source.put("acceptedFromLog", log.acceptedEvents());
        source.put("malformedLines", log.malformedLines());
        source.put("duplicateEvents", log.duplicateEvents());
        source.put("scope", log.evictedEvents() > 0
                ? "Counts cover the most recent " + log.indexedEvents() + " events; older events were dropped from the in-memory index."
                : "Counts cover every event read from the log.");
        out.put("source", source);
        return out;
    }

    private static Map<String, Object> timeline(List<EventSummary> events, long first, long last, Long override) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (events.isEmpty()) {
            out.put("bucketSeconds", null);
            out.put("buckets", List.of());
            return out;
        }
        long span = Math.max(1, (last - first) / 1000);
        long size = BUCKET_SECONDS[BUCKET_SECONDS.length - 1];
        if (override != null && override >= 1 && override <= 86_400) {
            size = override;
        } else {
            for (long candidate : BUCKET_SECONDS) {
                if (span / candidate <= 90) {
                    size = candidate;
                    break;
                }
            }
        }
        long ms = size * 1000;
        long start = (first / ms) * ms;
        long end = (last / ms) * ms;
        int count = (int) Math.min(2_000, (end - start) / ms + 1);
        long[] totals = new long[count];
        long[] flaggedCounts = new long[count];
        long[] blockedCounts = new long[count];
        for (EventSummary e : events) {
            int index = (int) ((e.ts() - start) / ms);
            if (index < 0 || index >= count) {
                continue;
            }
            totals[index]++;
            if (e.flagged()) {
                flaggedCounts[index]++;
            }
            if (e.blocked()) {
                blockedCounts[index]++;
            }
        }
        List<Map<String, Object>> buckets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("t", start + i * ms);
            bucket.put("total", totals[i]);
            bucket.put("flagged", flaggedCounts[i]);
            bucket.put("blocked", blockedCounts[i]);
            buckets.add(bucket);
        }
        out.put("bucketSeconds", size);
        out.put("buckets", buckets);
        return out;
    }

    private static List<Map<String, Object>> histogram(long[] bins) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < bins.length; i++) {
            Map<String, Object> bin = new LinkedHashMap<>();
            bin.put("from", i * 10);
            bin.put("to", i == bins.length - 1 ? 100 : i * 10 + 9);
            bin.put("count", bins[i]);
            out.add(bin);
        }
        return out;
    }

    private static int bin(int score) {
        return Math.max(0, Math.min(9, score / 10));
    }

    private static void bump(Map<String, Long> map, String key) {
        map.merge(key, 1L, Long::sum);
    }

    private static Map<String, Long> sortedDesc(Map<String, Long> map) {
        Map<String, Long> out = new LinkedHashMap<>();
        map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> out.put(entry.getKey(), entry.getValue()));
        return out;
    }
}
