package com.provguard.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Read-only JSON API plus the static dashboard.
 *
 * <p>Only GET and HEAD are accepted. Static files are served from the {@code /web} classpath folder through a
 * strict name whitelist; there is no filesystem access, shell access, or management endpoint. Responses carry a
 * restrictive Content-Security-Policy, and the Host header is checked so a web page on another origin cannot reach
 * the loopback service through DNS rebinding.
 */
public final class ApiServer implements AutoCloseable {
    private static final Pattern STATIC_PATH = Pattern.compile("/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*");
    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final int MAX_EXPORT = 100_000;

    private final WebConfig config;
    private final EventLogIndex index;
    private final SseHub hub;
    private final Instant startedAt;
    private final String version;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> allowedHosts;
    private HttpServer server;

    public ApiServer(WebConfig config, EventLogIndex index, SseHub hub, Instant startedAt, String version) {
        this.config = config;
        this.index = index;
        this.hub = hub;
        this.startedAt = startedAt;
        this.version = version;
        Set<String> hosts = new java.util.HashSet<>(Set.of("localhost", "127.0.0.1", "[::1]"));
        hosts.add(config.host().toLowerCase());
        this.allowedHosts = Set.copyOf(hosts);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "provguard-web-http");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                error(exchange, 405, "method_not_allowed", "The dashboard API is read-only.");
                return;
            }
            if (!hostAllowed(exchange)) {
                error(exchange, 403, "host_not_allowed", "Unexpected Host header.");
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            if (path.startsWith("/api/") || path.equals("/api")) {
                api(exchange, stripTrailingSlash(path), parseQuery(exchange.getRequestURI().getRawQuery()));
            } else {
                staticFile(exchange, path);
            }
        } catch (Throwable failure) {
            System.err.println("ProvGuard web: request failed: " + failure.getClass().getSimpleName());
            try {
                error(exchange, 500, "internal_error", "The request could not be completed.");
            } catch (IOException | RuntimeException ignored) {
                exchange.close();
            }
        }
    }

    private boolean hostAllowed(HttpExchange exchange) {
        if (config.allowRemote()) {
            return true;
        }
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null) {
            return false;
        }
        host = host.toLowerCase();
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(']') < colon) {
            host = host.substring(0, colon);
        } else if (colon > 0 && !host.startsWith("[")) {
            host = host.substring(0, colon);
        }
        return allowedHosts.contains(host);
    }

    private void api(HttpExchange exchange, String path, Map<String, String> query) throws IOException {
        switch (path) {
            case "/api/health" -> json(exchange, 200, HealthReport.build(config, index, startedAt, version));
            case "/api/events" -> events(exchange, query);
            case "/api/stats" -> stats(exchange, query);
            case "/api/policies" -> json(exchange, 200, PolicyCatalog.describe(ruleMatches()));
            case "/api/model" -> json(exchange, 200, ModelInfo.describe(config.reportsDir()));
            case "/api/stream" -> stream(exchange, query);
            case "/api/export" -> export(exchange, query);
            default -> {
                if (path.startsWith("/api/events/")) {
                    eventDetail(exchange, path.substring("/api/events/".length()));
                } else {
                    error(exchange, 404, "not_found", "Unknown API path.");
                }
            }
        }
    }

    private Map<String, Long> ruleMatches() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (EventSummary e : index.snapshot()) {
            for (String rule : e.matchedRules()) {
                counts.merge(rule, 1L, Long::sum);
            }
        }
        return counts;
    }

    private void events(HttpExchange exchange, Map<String, String> query) throws IOException {
        EventFilter filter = EventFilter.fromQuery(query);
        int limit = clamp(parseInt(query.get("limit"), DEFAULT_LIMIT), 1, MAX_LIMIT);
        EventLogIndex.Page page = index.query(filter, limit, parseLong(query.get("before")), parseLong(query.get("after")));
        List<Map<String, Object>> items = new ArrayList<>(page.items().size());
        for (EventSummary e : page.items()) {
            items.add(ApiJson.summary(e));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("totalMatching", page.totalMatching());
        out.put("nextBefore", page.nextBefore());
        out.put("truncated", page.truncated());
        out.put("latestSeq", page.latestSeq());
        out.put("epoch", page.epoch());
        out.put("limit", limit);
        json(exchange, 200, out);
    }

    private void stats(HttpExchange exchange, Map<String, String> query) throws IOException {
        EventFilter filter = EventFilter.fromQuery(query);
        List<EventSummary> events = index.snapshot();
        if (!filter.isEmpty()) {
            events = events.stream().filter(filter::matches).toList();
        }
        Map<String, Object> out = StatsCalculator.compute(events, index.status(), parseLong(query.get("bucket")));
        out.put("epoch", index.epoch());
        out.put("latestSeq", index.latestSeq());
        out.put("filtered", !filter.isEmpty());
        json(exchange, 200, out);
    }

    private void eventDetail(HttpExchange exchange, String id) throws IOException {
        if (!EventParser.SAFE_ID.matcher(id).matches()) {
            error(exchange, 400, "bad_id", "Event ids contain letters, digits, and hyphens only.");
            return;
        }
        EventSummary summary = index.find(id);
        if (summary == null) {
            error(exchange, 404, "event_not_found", "No event with that id is in the current index.");
            return;
        }
        try {
            JsonNode record = index.readRecord(summary);
            json(exchange, 200, index.parser().detail(record, summary));
        } catch (EventLogIndex.SourceChanged changed) {
            error(exchange, 409, "source_changed", changed.getMessage() + ". Reload the dashboard.");
        } catch (IOException failure) {
            error(exchange, 503, "log_unreadable", "The event log could not be read.");
        }
    }

    private void stream(HttpExchange exchange, Map<String, String> query) throws IOException {
        SseHub.Client client = hub.register();
        if (client == null) {
            error(exchange, 503, "too_many_streams", "Too many live streams are open.");
            return;
        }
        try {
            Long lastId = parseLong(exchange.getRequestHeaders().getFirst("Last-Event-ID"));
            if (lastId == null) {
                lastId = parseLong(query.get("after"));
            }
            Headers headers = exchange.getResponseHeaders();
            securityHeaders(headers);
            headers.set("Content-Type", "text/event-stream; charset=utf-8");
            headers.set("Cache-Control", "no-store");
            headers.set("X-Accel-Buffering", "no");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            Map<String, Object> hello = new LinkedHashMap<>();
            hello.put("epoch", index.epoch());
            hello.put("latestSeq", index.latestSeq());
            hello.put("serverTime", Instant.now().toString());
            hello.put("retryMs", 3000);
            write(out, "hello", -1, hub.json(hello));
            long sent = -1;
            if (lastId != null && lastId >= 0) {
                List<EventSummary> missed = new ArrayList<>(index.query(EventFilter.none(), 500, null, lastId).items());
                Collections.reverse(missed);
                for (EventSummary e : missed) {
                    write(out, "security-event", e.seq(), hub.json(ApiJson.summary(e)));
                    sent = Math.max(sent, e.seq());
                }
            }
            while (true) {
                SseHub.Message message = client.queue().poll(15, TimeUnit.SECONDS);
                if (message == null) {
                    out.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } else if (message.seq() < 0 || message.seq() > sent) {
                    write(out, message.event(), message.seq(), message.data());
                    sent = Math.max(sent, message.seq());
                }
                if (client.overflowed()) {
                    write(out, "overflow", -1, "{}");
                    break;
                }
            }
        } catch (IOException | InterruptedException closed) {
            // The browser went away or the server is stopping.
        } finally {
            hub.unregister(client);
            exchange.close();
        }
    }

    private static void write(OutputStream out, String event, long seq, String data) throws IOException {
        StringBuilder frame = new StringBuilder();
        if (seq >= 0) {
            frame.append("id: ").append(seq).append('\n');
        }
        frame.append("event: ").append(event).append('\n');
        frame.append("data: ").append(data.replace("\n", " ")).append("\n\n");
        out.write(frame.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void export(HttpExchange exchange, Map<String, String> query) throws IOException {
        String format = query.getOrDefault("format", "csv").toLowerCase();
        if (!format.equals("csv") && !format.equals("jsonl")) {
            error(exchange, 400, "bad_format", "format must be csv or jsonl.");
            return;
        }
        EventFilter filter = EventFilter.fromQuery(query);
        int limit = clamp(parseInt(query.get("limit"), 10_000), 1, MAX_EXPORT);
        List<EventSummary> page = new ArrayList<>(index.query(filter, limit, null, null).items());
        Collections.reverse(page);

        Headers headers = exchange.getResponseHeaders();
        securityHeaders(headers);
        boolean csv = format.equals("csv");
        headers.set("Content-Type", csv ? "text/csv; charset=utf-8" : "application/x-ndjson; charset=utf-8");
        headers.set("Content-Disposition", "attachment; filename=\"provguard-events." + (csv ? "csv" : "jsonl") + "\"");
        headers.set("Cache-Control", "no-store");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            if (csv) {
                out.write(Csv.header().getBytes(StandardCharsets.UTF_8));
                for (EventSummary e : page) {
                    out.write(Csv.row(e).getBytes(StandardCharsets.UTF_8));
                }
            } else {
                for (EventSummary e : page) {
                    Object value;
                    try {
                        value = index.parser().detail(index.readRecord(e), e);
                    } catch (EventLogIndex.SourceChanged | IOException unavailable) {
                        Map<String, Object> fallback = ApiJson.summary(e);
                        fallback.put("detailUnavailable", true);
                        value = fallback;
                    }
                    out.write(mapper.writeValueAsBytes(value));
                    out.write('\n');
                }
            }
        }
    }

    private void staticFile(HttpExchange exchange, String rawPath) throws IOException {
        String path = rawPath.equals("/") ? "/index.html" : rawPath;
        if (!STATIC_PATH.matcher(path).matches() || path.contains("..")) {
            error(exchange, 404, "not_found", "Not found.");
            return;
        }
        String type = contentType(path);
        if (type == null) {
            error(exchange, 404, "not_found", "Not found.");
            return;
        }
        byte[] body;
        try (InputStream in = ApiServer.class.getResourceAsStream("/web" + path)) {
            if (in == null) {
                error(exchange, 404, "not_found", "Not found.");
                return;
            }
            body = in.readAllBytes();
        }
        Headers headers = exchange.getResponseHeaders();
        securityHeaders(headers);
        headers.set("Content-Type", type);
        headers.set("Cache-Control", "no-cache");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            headers.set("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return null;
    }

    private void json(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        Headers headers = exchange.getResponseHeaders();
        securityHeaders(headers);
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            headers.set("Content-Length", String.valueOf(bytes.length));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void error(HttpExchange exchange, int status, String code, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        json(exchange, status, body);
    }

    private static void securityHeaders(Headers headers) {
        headers.set("Content-Security-Policy", CSP);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Cross-Origin-Resource-Policy", "same-origin");
        headers.set("X-Frame-Options", "DENY");
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            out.putIfAbsent(key, value);
        }
        return out;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String stripTrailingSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return raw == null ? fallback : Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Long parseLong(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : Long.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
