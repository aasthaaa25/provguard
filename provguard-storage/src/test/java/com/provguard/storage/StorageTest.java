package com.provguard.storage;

import com.provguard.core.Decision;
import com.provguard.core.RiskLevel;
import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {

    @Test
    void fullQueueDropsTheEventAndDoesNotThrow() {
        EventQueue queue = new EventQueue(1);
        SecurityEvent event = SecurityEvent.builder().sinkType(SinkType.RUNTIME_EXEC).build();
        RiskResult result = new RiskResult(0, null, 0, RiskLevel.LOW, Decision.ALLOW, Decision.ALLOW,
                List.of(), "RULES_ONLY", "", false);
        assertTrue(queue.offer(new EventQueue.PendingEvent(event, result)));
        assertFalse(queue.offer(new EventQueue.PendingEvent(event, result)));
        EventStatistics statistics = new EventStatistics();
        statistics.onCaptured(result, false);
        assertEquals(1, statistics.dropped());
    }

    @Test
    void jsonlWriterOmitsRawSecretsAndReportReadsTheFile() throws Exception {
        Path dir = Files.createTempDirectory("provguard-jsonl");
        Path jsonl = dir.resolve("events.jsonl");
        JsonlEventWriter writer = new JsonlEventWriter(jsonl, com.provguard.core.AgentMode.MONITOR);
        SecurityEvent event = SecurityEvent.builder()
                .sinkType(SinkType.PROCESS_BUILDER_START)
                .sinkMethod("ProcessBuilder.start()")
                .pathSignature("SafeDemoApplication>ProcessBuilder.start")
                .argumentHash("sha256:abc")
                .redactedArgumentPreview("cmd.exe /c echo to...")
                .argumentLength(48)
                .keywordRiskScore(0)
                .build();
        RiskResult result = new RiskResult(0, null, 0, RiskLevel.LOW, Decision.ALLOW, Decision.ALLOW,
                List.of(), "ML_UNAVAILABLE", "rules", false);
        writer.write(event, result);
        writer.close();
        String body = Files.readString(jsonl);
        assertFalse(body.contains("supersecretvalue-do-not-store"));
        assertTrue(body.contains("sha256:abc"));
        assertTrue(body.contains("PROCESS_BUILDER_START"));
        String summary = new ReportGenerator().summarize(jsonl);
        assertTrue(summary.contains("Events: 1"));
        Path csv = dir.resolve("events.csv");
        new ReportGenerator().writeCsv(jsonl, csv);
        assertTrue(Files.readString(csv).contains("event_id"));
    }
}
