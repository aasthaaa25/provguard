package com.provguard.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewWorkflowTest {
    @TempDir Path directory;

    @Test
    void createsReadsUpdatesAndDeletesPersistedCases() throws Exception {
        Path db = directory.resolve("review.sqlite");
        var store = new SqliteCaseStore(db);
        var item = item("Triage");
        store.insert(item);
        assertEquals(item, new SqliteCaseStore(db).find(item.id()).orElseThrow());
        var updated = new ReviewCase(item.id(), item.openedAt(), "Reviewed", item.source(),
                ReviewCase.Status.CLOSED, "Dismissed following review.");
        store.update(updated);
        assertEquals(List.of(updated), store.list());
        store.delete(item.id());
        assertTrue(store.find(item.id()).isEmpty());
    }

    @Test
    void importsEachEventOnceAndPreservesCaseEdits() throws Exception {
        var store = new SqliteCaseStore(directory.resolve("review.sqlite"));
        var event = UUID.randomUUID();
        Path log = directory.resolve("events.jsonl");
        String line = """
                {"eventId":"%s","timestamp":"2026-01-02T10:00:00Z","sinkType":"RUNTIME_EXEC","decision":"ALERT"}
                """.formatted(event);
        Files.writeString(log, line.repeat(2));
        var items = EventCaseImporter.read(log);
        assertEquals(1, items.size());
        assertEquals(1, store.importCases(items));
        var original = store.find(event).orElseThrow();
        var edited = new ReviewCase(event, original.openedAt(), "Reviewed event", original.source(),
                ReviewCase.Status.IN_REVIEW, "Keep this note.");
        store.update(edited);
        assertEquals(0, store.importCases(items));
        assertEquals(edited, store.find(event).orElseThrow());
        Files.writeString(log, line + "{bad");
        assertTrue(assertThrows(ReviewException.class, () -> EventCaseImporter.read(log)).getMessage().contains("line 2"));
    }

    @Test
    void rollsBackEarlierInsertsWhenAStatementFails() throws Exception {
        Path db = directory.resolve("review.sqlite");
        var store = new SqliteCaseStore(db);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TRIGGER reject_case BEFORE INSERT ON cases
                    WHEN NEW.title = 'rejected' BEGIN SELECT RAISE(ABORT, 'test failure'); END
                    """);
        }
        assertThrows(ReviewException.class, () -> store.importCases(List.of(item("first"), item("rejected"))));
        assertTrue(store.list().isEmpty());
    }

    @Test
    void validatesTitleAndNotes() {
        assertThrows(IllegalArgumentException.class, () -> item("  "));
        var item = item("Valid");
        assertThrows(IllegalArgumentException.class, () -> new ReviewCase(item.id(), item.openedAt(), item.title(),
                item.source(), item.status(), "x".repeat(4001)));
    }

    private static ReviewCase item(String title) {
        return new ReviewCase(UUID.randomUUID(), LocalDateTime.of(2026, 1, 2, 14, 0),
                title, "Manual", ReviewCase.Status.OPEN, "");
    }
}
