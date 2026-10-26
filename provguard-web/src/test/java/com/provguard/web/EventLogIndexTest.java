package com.provguard.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventLogIndexTest {
    @TempDir
    Path dir;

    private static String ev(int n, String decision) {
        return Samples.line(String.format("00000000-0000-4000-8000-%012d", n), "2026-10-24T10:00:%02d.000000000Z".formatted(n % 60),
                "RUNTIME_EXEC", decision, decision, false, "ALERT", 0, 10);
    }

    private static void append(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    void missingFileReportsMissingAndDoesNotFail() {
        EventLogIndex index = new EventLogIndex(dir.resolve("none.jsonl"), 1000, 1000);
        index.poll();
        EventLogIndex.Status s = index.status();
        assertEquals("missing", s.state());
        assertFalse(s.exists());
        assertEquals(0, s.indexedEvents());
    }

    @Test
    void emptyFileIsOkWithNoEvents() throws IOException {
        Path file = Files.createFile(dir.resolve("e.jsonl"));
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        index.poll();
        assertEquals("ok", index.status().state());
        assertEquals(0, index.status().indexedEvents());
        assertNull(index.latest());
    }

    @Test
    void malformedBlankAndDuplicateLinesAreSkippedAndCounted() throws IOException {
        Path file = dir.resolve("m.jsonl");
        append(file, ev(1, "ALLOW") + "\n{broken\n\n[1]\n" + ev(1, "ALLOW") + "\n" + ev(2, "ALERT") + "\n");
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        index.poll();
        EventLogIndex.Status s = index.status();
        assertEquals(2, s.indexedEvents());
        assertEquals(2, s.malformedLines());
        assertEquals(1, s.duplicateEvents());
        assertEquals(2, s.recentMalformed().size());
    }

    @Test
    void partialTrailingLineWaitsForItsNewlineAndIsNotCountedTwice() throws IOException {
        Path file = dir.resolve("p.jsonl");
        String second = ev(2, "ALERT");
        append(file, ev(1, "ALLOW") + "\n" + second.substring(0, 40));
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        index.poll();
        assertEquals(1, index.status().indexedEvents());
        assertEquals(0, index.status().malformedLines());
        append(file, second.substring(40) + "\n");
        index.poll();
        assertEquals(2, index.status().indexedEvents());
        assertEquals(0, index.status().malformedLines());
        index.poll();
        assertEquals(2, index.status().indexedEvents());
    }

    @Test
    void repeatedPollsNeverDoubleCountEvents() throws IOException {
        Path file = dir.resolve("r.jsonl");
        append(file, ev(1, "ALLOW") + "\n" + ev(2, "ALLOW") + "\n");
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        for (int i = 0; i < 5; i++) {
            index.poll();
        }
        assertEquals(2, index.status().acceptedEvents());
        assertEquals(2, index.latestSeq());
    }

    @Test
    void truncationResetsTheIndexAndNotifiesListeners() throws IOException {
        Path file = dir.resolve("t.jsonl");
        append(file, ev(1, "ALLOW") + "\n" + ev(2, "ALLOW") + "\n" + ev(3, "ALLOW") + "\n");
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        List<String> seen = new ArrayList<>();
        index.addListener(new EventLogIndex.Listener() {
            @Override public void onEvent(EventSummary s) { seen.add("event"); }
            @Override public void onReset(long epoch) { seen.add("reset"); }
        });
        index.poll();
        long epoch = index.epoch();
        Files.writeString(file, ev(9, "ALERT") + "\n", StandardCharsets.UTF_8);
        index.poll();
        assertEquals(1, index.status().indexedEvents());
        assertEquals(1, index.status().resets());
        assertTrue(index.epoch() > epoch);
        assertTrue(seen.contains("reset"));
        assertNull(index.find("00000000-0000-4000-8000-000000000001"));
    }

    @Test
    void replacedFileWithSameOrLargerSizeIsDetectedByItsHead() throws IOException {
        Path file = dir.resolve("h.jsonl");
        append(file, ev(1, "ALLOW") + "\n");
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        index.poll();
        Files.writeString(file, ev(5, "ALERT") + "\n" + ev(6, "ALERT") + "\n", StandardCharsets.UTF_8);
        index.poll();
        assertEquals(1, index.status().resets());
        assertEquals(2, index.status().indexedEvents());
        assertNull(index.find("00000000-0000-4000-8000-000000000001"));
    }

    @Test
    void deletedFileResetsTheIndex() throws IOException {
        Path file = dir.resolve("d.jsonl");
        append(file, ev(1, "ALLOW") + "\n");
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        index.poll();
        Files.delete(file);
        index.poll();
        assertEquals("missing", index.status().state());
        assertEquals(0, index.status().indexedEvents());
    }

    @Test
    void indexIsBoundedButTotalsRemainHonest() throws IOException {
        Path file = dir.resolve("b.jsonl");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 400; i++) {
            sb.append(ev(i, "ALLOW")).append('\n');
        }
        append(file, sb.toString());
        EventLogIndex index = new EventLogIndex(file, 100, 1000);
        index.poll();
        EventLogIndex.Status s = index.status();
        assertTrue(s.indexedEvents() <= 100 + 16 + 10);
        assertEquals(400, s.acceptedEvents());
        assertEquals(400 - s.indexedEvents(), s.evictedEvents());
        assertNotNull(index.latest());
        assertEquals(400, index.latest().seq());
    }

    @Test
    void detailIsReadFromTheOriginalLineOnDemand() throws IOException, EventLogIndex.SourceChanged {
        Path file = dir.resolve("x.jsonl");
        append(file, ev(1, "ALLOW") + "\n" + ev(2, "ALERT") + "\n");
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        index.poll();
        EventSummary second = index.find("00000000-0000-4000-8000-000000000002");
        JsonNode record = index.readRecord(second);
        assertEquals("ALERT", record.get("decision").asText());
        Files.writeString(file, "x".repeat(10) + "\n", StandardCharsets.UTF_8);
        assertThrows(EventLogIndex.SourceChanged.class, () -> index.readRecord(second));
    }

    @Test
    void queryFiltersAndPagesNewestFirst() throws IOException {
        Path file = dir.resolve("q.jsonl");
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            sb.append(ev(i, i % 2 == 0 ? "ALERT" : "ALLOW")).append('\n');
        }
        append(file, sb.toString());
        EventLogIndex index = new EventLogIndex(file, 1000, 1000);
        index.poll();
        EventLogIndex.Page all = index.query(EventFilter.none(), 4, null, null);
        assertEquals(10, all.totalMatching());
        assertEquals(10, all.items().get(0).seq());
        assertTrue(all.truncated());
        EventLogIndex.Page older = index.query(EventFilter.none(), 4, all.nextBefore(), null);
        assertEquals(6, older.items().get(0).seq());
        EventFilter alerts = EventFilter.fromQuery(java.util.Map.of("decision", "alert"));
        assertEquals(5, index.query(alerts, 100, null, null).totalMatching());
        EventLogIndex.Page newer = index.query(EventFilter.none(), 100, null, 8L);
        assertEquals(2, newer.items().size());
        EventFilter search = EventFilter.fromQuery(java.util.Map.of("q", "DEMO.HELPER.RUN"));
        assertEquals(10, index.query(search, 100, null, null).totalMatching());
    }
}
