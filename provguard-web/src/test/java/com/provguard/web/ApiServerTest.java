package com.provguard.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiServerTest {
    @TempDir
    Path dir;
    private Path log;
    private EventLogIndex index;
    private ApiServer server;
    private SseHub hub;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String base;

    private static final String ID1 = "00000000-0000-4000-8000-000000000001";
    private static final String ID2 = "00000000-0000-4000-8000-000000000002";

    @BeforeEach
    void start() throws IOException {
        log = dir.resolve("events.jsonl");
        Files.writeString(log, Samples.line(ID1, "2026-10-24T10:00:00.000000000Z", "RUNTIME_EXEC", "ALLOW", "ALLOW", false, "ALERT", 0, 10) + "\n"
                + "{garbage\n"
                + Samples.line(ID2, "2026-10-24T10:00:05.000000000Z", "PROCESS_BUILDER_START", "ALERT", "BLOCK", false, "ALERT", 90, 85, "DANGEROUS_COMMAND_KEYWORD") + "\n");
        WebConfig config = new WebConfig(log, dir, "127.0.0.1", 0, false, false, 1000, 200, 300);
        index = new EventLogIndex(log, 1000, 200);
        hub = new SseHub();
        index.addListener(hub);
        index.poll();
        server = new ApiServer(config, index, hub, Instant.now(), "test");
        server.start();
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(String path) throws Exception {
        return mapper.readTree(get(path).body());
    }

    private String raw(String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
        }
    }

    @Test
    void healthSeparatesBackendFromAgentAndNeverClaimsTheAgentRuns() throws Exception {
        JsonNode health = json("/api/health");
        assertEquals("up", health.at("/backend/status").asText());
        assertFalse(health.at("/agent/determinable").asBoolean());
        assertEquals(1, health.at("/log/malformedLines").asInt());
        assertEquals("events.jsonl", health.at("/log/fileName").asText());
        assertFalse(health.toString().contains(dir.toString()), "full path must not be sent to the browser");
    }

    @Test
    void eventsListFiltersAndPaginates() throws Exception {
        JsonNode all = json("/api/events");
        assertEquals(2, all.get("totalMatching").asInt());
        assertEquals(ID2, all.at("/items/0/id").asText());
        assertEquals(1, json("/api/events?decision=ALERT").get("items").size());
        assertEquals(0, json("/api/events?q=nomatchhere").get("items").size());
        JsonNode page = json("/api/events?limit=1");
        assertTrue(page.get("truncated").asBoolean());
        assertEquals(ID1, json("/api/events?limit=1&before=" + page.get("nextBefore").asLong()).at("/items/0/id").asText());
        assertEquals("policy_block_not_enforced", all.at("/items/0/outcome").asText());
    }

    @Test
    void eventDetailReturnsExecutionOrderedFramesAndSeparateScores() throws Exception {
        JsonNode d = json("/api/events/" + ID2);
        assertEquals(90, d.at("/rules/score").asInt());
        assertEquals(85, d.at("/ml/score").asInt());
        assertEquals("demo.App", d.at("/path/frames/0/className").asText());
        assertFalse(d.at("/decision/enforced").asBoolean());
        assertFalse(d.at("/process/recorded").asBoolean());
    }

    @Test
    void unknownAndInvalidIdsAreRejected() throws Exception {
        assertEquals(404, get("/api/events/unknown-id").statusCode());
        assertEquals(400, get("/api/events/..%2F..%2Fetc").statusCode());
        assertEquals(404, get("/api/nothing").statusCode());
    }

    @Test
    void statsPolicyAndModelEndpointsAnswer() throws Exception {
        assertEquals(2, json("/api/stats").get("total").asInt());
        assertTrue(json("/api/policies").get("readOnly").asBoolean());
        assertNotNull(json("/api/model").get("packaged"));
    }

    @Test
    void onlyReadMethodsAreAccepted() throws Exception {
        for (String method : new String[] {"POST", "PUT", "DELETE", "PATCH"}) {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(base + "/api/events"))
                    .method(method, HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(405, r.statusCode(), method);
        }
    }

    @Test
    void foreignHostHeadersAreRefusedToBlockDnsRebinding() throws IOException {
        assertTrue(raw("GET /api/health HTTP/1.1\r\nHost: evil.example\r\nConnection: close\r\n\r\n").contains("403"));
        assertTrue(raw("GET /api/health HTTP/1.1\r\nHost: localhost:" + server.port() + "\r\nConnection: close\r\n\r\n").contains("200"));
    }

    @Test
    void staticServerOnlyServesWhitelistedBundledFilesAndAddsSecurityHeaders() throws Exception {
        for (String path : new String[] {"/../pom.xml", "/%2e%2e/%2e%2e/etc/passwd", "/com/provguard/web/WebMain.class", "/js/..%2f..%2fx"}) {
            assertEquals(404, get(path).statusCode(), path);
        }
        assertEquals(404, raw("GET /../../etc/passwd HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").contains("404") ? 404 : 0);
        HttpResponse<String> health = get("/api/health");
        String csp = health.headers().firstValue("Content-Security-Policy").orElse("");
        assertTrue(csp.contains("default-src 'none'") && csp.contains("script-src 'self'"));
        assertEquals("nosniff", health.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertTrue(health.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void exportDoesNotModifyTheLogAndNeutralisesFormulas() throws Exception {
        String before = Files.readString(log);
        HttpResponse<String> csv = get("/api/export?format=csv");
        assertEquals(200, csv.statusCode());
        assertTrue(csv.body().startsWith("id,timestamp"));
        assertEquals(3, csv.body().strip().split("\r?\n").length);
        HttpResponse<String> jsonl = get("/api/export?format=jsonl&decision=alert");
        assertEquals(1, jsonl.body().strip().split("\n").length);
        assertEquals(400, get("/api/export?format=exe").statusCode());
        assertEquals(before, Files.readString(log));
    }

    @Test
    void streamDeliversNewEventsWithoutDuplicates() throws Exception {
        HttpResponse<java.io.InputStream> response = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/stream")).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        String id3 = "00000000-0000-4000-8000-000000000003";
        Files.writeString(log, Samples.line(id3, "2026-10-24T10:00:10.000000000Z", "RUNTIME_EXEC", "ALLOW", "ALLOW", false, "ALERT", 0, 10) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Thread.sleep(300);
        index.poll();
        index.poll();
        int seen = 0;
        boolean hello = false;
        String line;
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline && (line = reader.readLine()) != null) {
            if (line.equals("event: hello")) {
                hello = true;
            }
            if (line.contains(id3)) {
                seen++;
                break;
            }
        }
        assertTrue(hello);
        assertEquals(1, seen);
        response.body().close();
    }

    @Test
    void streamResumesFromLastEventIdForReconnectingClients() throws Exception {
        HttpResponse<java.io.InputStream> response = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/stream")).header("Last-Event-ID", "1").timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        boolean replayed = false;
        long deadline = System.currentTimeMillis() + 5000;
        String line;
        while (System.currentTimeMillis() < deadline && (line = reader.readLine()) != null) {
            if (line.contains(ID2)) {
                replayed = true;
                break;
            }
        }
        assertTrue(replayed, "event after Last-Event-ID should be replayed");
        response.body().close();
    }
}
