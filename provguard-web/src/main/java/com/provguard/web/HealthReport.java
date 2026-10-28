package com.provguard.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backend health and agent health are different things. The backend knows only that this process is serving.
 * The agent has no heartbeat, so the only evidence available is what it wrote to the log.
 */
public final class HealthReport {
    private HealthReport() {
    }

    public static Map<String, Object> build(WebConfig config, EventLogIndex index, Instant startedAt, String version) {
        long now = System.currentTimeMillis();
        EventLogIndex.Status log = index.status();
        EventSummary latest = index.latest();

        Map<String, Object> backend = new LinkedHashMap<>();
        backend.put("status", "up");
        backend.put("version", version);
        backend.put("startedAt", startedAt.toString());
        backend.put("uptimeSeconds", Math.max(0, (now - startedAt.toEpochMilli()) / 1000));
        backend.put("bind", config.host() + ":" + config.port());
        backend.put("localOnly", !config.allowRemote());
        backend.put("demoMode", config.demo());
        backend.put("pollIntervalMs", config.pollMillis());

        Map<String, Object> agent = new LinkedHashMap<>();
        String status;
        Long ageSeconds = null;
        if (!log.exists()) {
            status = "no_log";
        } else if (latest == null) {
            status = "no_events";
        } else {
            ageSeconds = Math.max(0, (now - latest.ts()) / 1000);
            status = ageSeconds <= config.activeWindowSeconds() ? "recent_activity" : "idle";
        }
        agent.put("status", status);
        agent.put("determinable", false);
        agent.put("basis", config.demo()
                ? "Demo mode. There is no agent; events are synthetic."
                : "Inferred from the event log only. ProvGuard has no heartbeat or status endpoint, so the backend cannot "
                + "confirm that an agent is running. A quiet log can mean an idle application, a stopped agent, or a different log path.");
        agent.put("activeWindowSeconds", config.activeWindowSeconds());
        agent.put("lastEventAt", latest == null ? null : latest.timestamp());
        agent.put("lastEventTs", latest == null ? null : latest.ts());
        agent.put("lastEventAgeSeconds", ageSeconds);
        agent.put("mode", latest == null ? null : latest.mode());
        agent.put("modeSource", "The mode field of the most recent logged event. It is the mode that event was recorded under, not a live setting.");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("kind", config.demo() ? "demo" : "jsonl-file");
        source.put("fileName", config.demo() ? "(synthetic demo events)" : config.eventsFile().getFileName().toString());
        source.put("state", log.state());
        source.put("exists", log.exists());
        source.put("sizeBytes", log.sizeBytes());
        source.put("lastModifiedMs", log.exists() ? log.lastModifiedMs() : null);
        source.put("linesRead", log.linesRead());
        source.put("acceptedEvents", log.acceptedEvents());
        source.put("indexedEvents", log.indexedEvents());
        source.put("evictedEvents", log.evictedEvents());
        source.put("maxIndexedEvents", log.maxIndexed());
        source.put("malformedLines", log.malformedLines());
        source.put("duplicateEvents", log.duplicateEvents());
        source.put("resets", log.resets());
        source.put("lastPollMs", log.lastPollMs());
        source.put("lastError", log.lastError());
        source.put("recentMalformed", log.recentMalformed());
        source.put("pathNote", "Only the file name is shown. The full path is not sent to the browser.");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serverTime", Instant.ofEpochMilli(now).toString());
        out.put("epoch", log.epoch());
        out.put("latestSeq", log.latestSeq());
        out.put("backend", backend);
        out.put("agent", agent);
        out.put("log", source);
        return out;
    }
}
