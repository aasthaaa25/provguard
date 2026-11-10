package com.provguard.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EventParser parser = new EventParser(mapper);

    private EventSummary summary(String line) throws Exception {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        JsonNode root = parser.readTree(bytes, bytes.length);
        return parser.summarize(root, bytes, bytes.length, 1, 0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detail(String line) throws Exception {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        JsonNode root = parser.readTree(bytes, bytes.length);
        return parser.detail(root, parser.summarize(root, bytes, bytes.length, 1, 0));
    }

    @Test
    void outcomeSeparatesFlaggedFromBlocked() {
        assertEquals("blocked", EventSummary.outcomeOf("BLOCK", "BLOCK", true));
        assertEquals("policy_block_not_enforced", EventSummary.outcomeOf("ALERT", "BLOCK", false));
        assertEquals("policy_block_not_enforced", EventSummary.outcomeOf("BLOCK", "BLOCK", false));
        assertEquals("flagged", EventSummary.outcomeOf("ALERT", "ALERT", false));
        assertEquals("allowed", EventSummary.outcomeOf("ALLOW", "ALLOW", false));
        assertEquals("unknown", EventSummary.outcomeOf(null, null, null));
    }

    @Test
    void flaggedIsNeverTreatedAsBlocked() throws Exception {
        EventSummary e = summary(Samples.line("11111111-1111-4111-8111-111111111111", "2026-10-24T10:00:00Z",
                "PROCESS_BUILDER_START", "ALERT", "BLOCK", false, "ALERT", 90, 80, "DANGEROUS_COMMAND_KEYWORD"));
        assertTrue(e.flagged());
        assertFalse(e.blocked());
    }

    @Test
    @SuppressWarnings("unchecked")
    void framesAreReturnedInExecutionOrderEntryFirstSinkLast() throws Exception {
        Map<String, Object> d = detail(Samples.allowed());
        Map<String, Object> path = (Map<String, Object>) d.get("path");
        List<Map<String, Object>> frames = (List<Map<String, Object>>) path.get("frames");
        assertEquals("demo.App", frames.get(0).get("className"));
        assertEquals("entry", frames.get(0).get("role"));
        assertEquals("java.lang.ProcessBuilder", frames.get(3).get("className"));
        assertEquals("sink", frames.get(3).get("role"));
        assertEquals(1, frames.get(0).get("position"));
        assertEquals("application", frames.get(0).get("category"));
        assertEquals("platform", frames.get(3).get("category"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onlyReflectionFramesCarryAFlag() throws Exception {
        Map<String, Object> d = detail(Samples.allowed());
        List<Map<String, Object>> frames = (List<Map<String, Object>>) ((Map<String, Object>) d.get("path")).get("frames");
        for (Map<String, Object> f : frames) {
            boolean reflective = String.valueOf(f.get("className")).startsWith("jdk.internal.reflect.");
            assertEquals(reflective, !((List<?>) f.get("flags")).isEmpty(), String.valueOf(f.get("className")));
        }
    }

    @Test
    void missingFieldsStayNullAndAreNeverInvented() throws Exception {
        EventSummary e = summary("{\"sinkType\":\"RUNTIME_EXEC\",\"timestamp\":\"2026-10-24T10:00:00Z\"}");
        assertNull(e.mlScore());
        assertNull(e.ruleScore());
        assertNull(e.enforced());
        assertNull(e.decision());
        assertEquals("unknown", e.outcome());
        assertTrue(e.derivedId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processInformationIsReportedAsNotRecorded() throws Exception {
        Map<String, Object> d = detail(Samples.allowed());
        assertEquals(Boolean.FALSE, ((Map<String, Object>) d.get("process")).get("recorded"));
    }

    @Test
    void derivedIdIsStableForTheSameLine() throws Exception {
        String line = "{\"sinkType\":\"RUNTIME_EXEC\",\"timestamp\":\"2026-10-24T10:00:00Z\"}";
        assertEquals(summary(line).id(), summary(line).id());
        assertTrue(summary(line).id().startsWith("derived-"));
    }

    @Test
    void unsafeEventIdsAreReplacedByDerivedIds() throws Exception {
        EventSummary e = summary("{\"eventId\":\"../../etc\",\"sinkType\":\"RUNTIME_EXEC\",\"timestamp\":\"2026-10-24T10:00:00Z\"}");
        assertTrue(e.derivedId());
        assertTrue(EventParser.SAFE_ID.matcher(e.id()).matches());
    }

    @Test
    void malformedRecordsAreRejectedWithAReason() {
        assertThrows(EventParser.MalformedRecord.class, () -> summary("not json"));
        assertThrows(EventParser.MalformedRecord.class, () -> summary("[1,2]"));
        assertThrows(EventParser.MalformedRecord.class, () -> summary("{\"timestamp\":\"2026-10-24T10:00:00Z\"}"));
        assertThrows(EventParser.MalformedRecord.class, () -> summary("{\"sinkType\":\"X\",\"timestamp\":\"yesterday\"}"));
    }

    @Test
    void controlCharactersAreStrippedAndLengthIsBounded() {
        assertEquals("a b c", EventParser.clean("a\nb\u0000c", 50));
        assertEquals(5, EventParser.clean("x".repeat(100), 5).length());
        assertNull(EventParser.clean(null, 5));
    }

    @Test
    void htmlInEventTextIsKeptAsDataNotInterpretedByTheBackend() throws Exception {
        String line = Samples.allowed().replace("\"main\"", "\"<img src=x onerror=alert(1)>\"");
        assertEquals("<img src=x onerror=alert(1)>", summary(line).threadName());
    }
}
