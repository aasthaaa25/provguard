package com.provguard.web;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Server-side filter shared by the events list, statistics and export. All fields are optional. */
public record EventFilter(
        String q,
        Set<String> sinks,
        Set<String> severities,
        Set<String> decisions,
        Set<String> outcomes,
        Set<String> modes,
        Long from,
        Long to) {

    public static EventFilter none() {
        return new EventFilter(null, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), null, null);
    }

    public static EventFilter fromQuery(Map<String, String> query) {
        String q = query.get("q");
        if (q != null) {
            q = q.trim().toLowerCase(Locale.ROOT);
            if (q.length() > 200) {
                q = q.substring(0, 200);
            }
            if (q.isEmpty()) {
                q = null;
            }
        }
        return new EventFilter(
                q,
                upperSet(query.get("sink")),
                upperSet(query.get("severity")),
                upperSet(query.get("decision")),
                lowerSet(query.get("outcome")),
                upperSet(query.get("mode")),
                instant(query.get("from")),
                instant(query.get("to")));
    }

    public boolean isEmpty() {
        return q == null && sinks.isEmpty() && severities.isEmpty() && decisions.isEmpty()
                && outcomes.isEmpty() && modes.isEmpty() && from == null && to == null;
    }

    public boolean matches(EventSummary e) {
        if (!sinks.isEmpty() && !sinks.contains(nullToEmpty(e.sinkType()))) {
            return false;
        }
        if (!severities.isEmpty() && !severities.contains(nullToEmpty(e.riskLevel()))) {
            return false;
        }
        if (!decisions.isEmpty() && !decisions.contains(nullToEmpty(e.decision()))) {
            return false;
        }
        if (!outcomes.isEmpty() && !outcomes.contains(nullToEmpty(e.outcome()))) {
            return false;
        }
        if (!modes.isEmpty() && !modes.contains(nullToEmpty(e.mode()))) {
            return false;
        }
        if (from != null && e.ts() < from) {
            return false;
        }
        if (to != null && e.ts() > to) {
            return false;
        }
        if (q == null) {
            return true;
        }
        return contains(e.sinkType(), q) || contains(e.sinkMethod(), q) || contains(e.entry(), q)
                || contains(e.pathSignature(), q) || contains(e.threadName(), q) || contains(e.id(), q)
                || contains(e.frameText(), q)
                || e.matchedRules().stream().anyMatch(rule -> contains(rule, q));
    }

    private static boolean contains(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Set<String> upperSet(String csv) {
        return split(csv, true);
    }

    private static Set<String> lowerSet(String csv) {
        return split(csv, false);
    }

    private static Set<String> split(String csv, boolean upper) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String part : csv.split(",")) {
            String value = part.trim();
            if (!value.isEmpty() && value.length() <= 64) {
                out.add(upper ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(out);
    }

    private static Long instant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim()).toEpochMilli();
        } catch (DateTimeParseException exception) {
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
