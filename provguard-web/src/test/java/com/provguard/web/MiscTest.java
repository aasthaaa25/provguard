package com.provguard.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiscTest {
    @TempDir
    Path dir;

    @Test
    void defaultsBindToLoopbackOnly() {
        WebConfig config = WebConfig.defaults();
        assertEquals("127.0.0.1", config.host());
        assertFalse(config.allowRemote());
    }

    @Test
    void nonLoopbackBindIsRefusedWithoutExplicitFlag() {
        assertThrows(IllegalArgumentException.class, () -> WebConfig.parse(new String[] {"--host", "0.0.0.0"}));
        assertEquals("0.0.0.0", WebConfig.parse(new String[] {"--host", "0.0.0.0", "--allow-remote"}).host());
    }

    @Test
    void badArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> WebConfig.parse(new String[] {"--port", "70000"}));
        assertThrows(IllegalArgumentException.class, () -> WebConfig.parse(new String[] {"--port", "abc"}));
        assertThrows(IllegalArgumentException.class, () -> WebConfig.parse(new String[] {"--nope"}));
        assertThrows(IllegalArgumentException.class, () -> WebConfig.parse(new String[] {"--events"}));
    }

    @Test
    void csvNeutralisesFormulaCellsAndQuotesSpecials() {
        assertEquals("'=cmd", Csv.escape("=cmd"));
        assertEquals("'@x", Csv.escape("@x"));
        assertEquals("\"a,b\"", Csv.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""));
        assertEquals("", Csv.escape(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void statisticsAreComputedOnlyFromRecords() throws IOException {
        Path file = dir.resolve("s.jsonl");
        String text = String.join("\n",
                Samples.line("00000000-0000-4000-8000-000000000001", "2026-10-24T10:00:00.000000000Z", "RUNTIME_EXEC", "ALLOW", "ALLOW", false, "ALERT", 0, 20),
                Samples.line("00000000-0000-4000-8000-000000000002", "2026-10-24T10:00:30.000000000Z", "RUNTIME_EXEC", "ALERT", "BLOCK", false, "ALERT", 90, 85, "UNSAFE_DESERIALIZATION_CLASS"),
                Samples.line("00000000-0000-4000-8000-000000000003", "2026-10-24T10:01:00.000000000Z", "PROCESS_BUILDER_START", "BLOCK", "BLOCK", true, "BLOCK", 90, null, "UNSAFE_DESERIALIZATION_CLASS")) + "\n";
        Files.writeString(file, text, StandardCharsets.UTF_8);
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        index.poll();
        Map<String, Object> stats = StatsCalculator.compute(index.snapshot(), index.status(), null);
        assertEquals(3L, stats.get("total"));
        assertEquals(2L, stats.get("flagged"));
        assertEquals(1L, stats.get("blocked"));
        assertEquals(1L, stats.get("policyBlockNotEnforced"));
        Map<String, Object> ml = (Map<String, Object>) stats.get("ml");
        assertEquals(2L, ml.get("scored"));
        assertEquals(1L, ml.get("unavailable"));
        assertEquals(1L, ml.get("highCount"));
        assertEquals(2L, ((Map<String, Long>) stats.get("bySink")).get("RUNTIME_EXEC"));
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) ((Map<String, Object>) stats.get("timeline")).get("buckets");
        assertEquals(3L, buckets.stream().mapToLong(b -> (Long) b.get("total")).sum());
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyStatisticsHaveNoInventedNumbers() {
        EventLogIndex index = new EventLogIndex(dir.resolve("none"), 1000, 1000);
        index.poll();
        Map<String, Object> stats = StatsCalculator.compute(index.snapshot(), index.status(), null);
        assertEquals(0L, stats.get("total"));
        assertNull(stats.get("firstEventTs"));
        assertNull(((Map<String, Object>) stats.get("ml")).get("mean"));
        assertTrue(((List<?>) ((Map<String, Object>) stats.get("timeline")).get("buckets")).isEmpty());
    }

    @Test
    void demoRecordsParseAndAreInternallyConsistent() {
        Random random = new Random(1);
        EventParser parser = new EventParser(new com.fasterxml.jackson.databind.ObjectMapper());
        for (int i = 0; i < 40; i++) {
            String line = DemoData.event(random, 1_760_000_000_000L + i, com.provguard.core.AgentMode.values()[i % 3]);
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            try {
                EventSummary e = parser.summarize(parser.readTree(bytes, bytes.length), bytes, bytes.length, i + 1, 0);
                boolean blockMode = "BLOCK".equals(e.mode());
                assertEquals(blockMode && "BLOCK".equals(e.policyDecision()), Boolean.TRUE.equals(e.enforced()));
            } catch (EventParser.MalformedRecord record) {
                throw new AssertionError(record);
            }
        }
    }

    @Test
    void evaluationReportIsParsedAndMissingMetricsAreNamed() throws IOException {
        Files.writeString(dir.resolve("ml-evaluation.md"),
                "# Report\n\nA held-out sample.\n\n- Recall at score 80: 0.9\n- Mean score: 30.0\n", StandardCharsets.UTF_8);
        Map<String, Object> eval = ModelInfo.evaluation(dir);
        assertEquals(Boolean.TRUE, eval.get("available"));
        assertEquals(2, ((List<?>) eval.get("metrics")).size());
        assertEquals(List.of("Precision", "F1"), eval.get("notReported"));
        assertEquals(Boolean.FALSE, ModelInfo.evaluation(dir.resolve("nope")).get("available"));
    }
}
