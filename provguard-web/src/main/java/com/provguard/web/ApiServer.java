package com.provguard.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
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

/**
 * Read-only JSON API plus the server-rendered dashboard.
 *
 * <p>The API is read-only. Dashboard form submissions save display preferences or start
 * the fixed teaching workloads in the opt-in local viva lab.
 */
public final class ApiServer implements AutoCloseable {
    private static final String CSP = "default-src 'none'; script-src 'none'; connect-src 'self'; style-src 'self'; frame-src 'self'; base-uri 'none'; "
            + "form-action 'self'; frame-ancestors 'none'";
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
    private final VivaLab viva;
    private HttpServer server;

    public ApiServer(WebConfig config, EventLogIndex index, SseHub hub, Instant startedAt, String version) {
        this(config, index, hub, startedAt, version, null);
    }

    ApiServer(WebConfig config, EventLogIndex index, SseHub hub, Instant startedAt, String version, VivaLab viva) {
        this.config = config;
        this.index = index;
        this.hub = hub;
        this.startedAt = startedAt;
        this.version = version;
        this.viva = viva;
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
        if (viva != null) {
            viva.close();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            if (viva != null && path.equals("/viva/run") && "POST".equals(method)) {
                startViva(exchange);
                return;
            }
            if (path.equals("/settings") && "POST".equals(method)) {
                saveSettings(exchange);
                return;
            }
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                error(exchange, 405, "method_not_allowed", "The dashboard API is read-only.");
                return;
            }
            if (!hostAllowed(exchange)) {
                error(exchange, 403, "host_not_allowed", "Unexpected Host header.");
                return;
            }
            var settings = WorkspaceSettings.read(exchange.getRequestHeaders().get("Cookie"));
            if (path.startsWith("/api/") || path.equals("/api")) {
                api(exchange, stripTrailingSlash(path), parseQuery(exchange.getRequestURI().getRawQuery()));
            } else if (path.equals("/live")) {
                sendPage(exchange, DashboardPage.live(index, parseQuery(exchange.getRequestURI().getRawQuery()), settings),
                        "text/html; charset=utf-8", true);
            } else if (path.equals("/dashboard.css")) {
                sendPage(exchange, DashboardStyle.CSS, "text/css; charset=utf-8", false);
            } else if (viva != null && path.equals("/viva/panel")) {
                sendPage(exchange, VivaPage.panel(viva, settings), "text/html; charset=utf-8", true);
            } else if (viva != null && path.startsWith("/viva/evidence/")) {
                vivaEvidence(exchange, path.substring("/viva/evidence/".length()), settings);
            } else if (viva != null && path.startsWith("/runs/") && path.length() > "/runs/".length()) {
                runAnalysis(exchange, stripTrailingSlash(path).substring("/runs/".length()), settings);
            } else {
                dashboard(exchange, path, settings);
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

    private void startViva(HttpExchange exchange) throws IOException {
        String expected = "token=" + viva.token();
        if (!sameOriginForm(exchange) || !exchange.getRemoteAddress().getAddress().isLoopbackAddress()
                || !expected.equals(new String(exchange.getRequestBody().readNBytes(expected.length() + 1),
                    StandardCharsets.US_ASCII))) {
            error(exchange, 403, "lab_request_rejected", "Open the viva page to start the local lab.");
            return;
        }
        viva.start();
        Headers response = exchange.getResponseHeaders();
        securityHeaders(response);
        frameHeaders(response);
        response.set("Location", "/viva/panel");
        response.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private boolean sameOriginForm(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        return hostAllowed(exchange) && headers.getFirst("Host") != null
                && ("http://" + headers.getFirst("Host")).equals(headers.getFirst("Origin"))
                && "application/x-www-form-urlencoded".equals(headers.getFirst("Content-Type"));
    }

    private void saveSettings(HttpExchange exchange) throws IOException {
        if (!sameOriginForm(exchange)) {
            error(exchange, 403, "settings_request_rejected", "Open Settings to save your display preferences.");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(257);
        if (body.length > 256) {
            error(exchange, 413, "settings_too_large", "Display settings exceeded the form size limit.");
            return;
        }
        WorkspaceSettings settings;
        try {
            settings = WorkspaceSettings.fromForm(parseQuery(new String(body, StandardCharsets.US_ASCII)));
        } catch (IllegalArgumentException invalid) {
            error(exchange, 400, "invalid_settings", invalid.getMessage());
            return;
        }
        Headers response = exchange.getResponseHeaders();
        securityHeaders(response);
        response.set("Location", "/settings?saved=1");
        response.set("Set-Cookie", settings.cookie());
        response.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private void runAnalysis(HttpExchange exchange, String path, WorkspaceSettings settings) throws IOException {
        String[] parts = path.split("/", -1);
        boolean download = parts.length == 2 && "evidence.zip".equals(parts[1]);
        var run = parts.length == 1 || download ? viva.evidence(parts[0]) : null;
        if (run == null) {
            sendPage(exchange, DashboardPage.missing(settings), "text/html; charset=utf-8", false, 404);
        } else if (download) {
            sendRunArchive(exchange, run);
        } else {
            sendPage(exchange, DashboardPage.runAnalysis(run, settings), "text/html; charset=utf-8", false);
        }
    }

    private void sendRunArchive(HttpExchange exchange, VivaLab.Evidence run) throws IOException {
        byte[] archive;
        try {
            archive = RunArchive.build(run.directory());
        } catch (IOException unavailable) {
            error(exchange, 503, "archive_unavailable", "The evidence ZIP could not be prepared.");
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        securityHeaders(headers);
        headers.set("Content-Type", "application/zip");
        headers.set("Content-Disposition", "attachment; filename=\"provguard-" + run.id() + ".zip\"");
        headers.set("Cache-Control", "no-store");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            headers.set("Content-Length", String.valueOf(archive.length));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, archive.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(archive);
        }
    }

    private void vivaEvidence(HttpExchange exchange, String path, WorkspaceSettings settings) throws IOException {
        String[] parts = path.split("/", -1);
        boolean record = parts.length == 3 && "record".equals(parts[2]);
        VivaLab.Evidence evidence = parts.length == 2 || record ? viva.evidence(parts[0]) : null;
        if (record && evidence != null) {
            eventDetail(exchange, parts[1], evidence.index());
            return;
        }
        EventSummary event = evidence != null && EventParser.SAFE_ID.matcher(parts[1]).matches()
                ? evidence.index().find(parts[1]) : null;
        sendPage(exchange, event == null ? DashboardPage.missing(settings) : DashboardPage.vivaEvidence(evidence, event, settings),
                "text/html; charset=utf-8", false, event == null ? 404 : 200);
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
                    eventDetail(exchange, path.substring("/api/events/".length()), index);
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

    private void eventDetail(HttpExchange exchange, String id, EventLogIndex index) throws IOException {
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
        if ("HEAD".equals(exchange.getRequestMethod())) {
            streamHeaders(exchange);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
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
            streamHeaders(exchange);
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
                var replay = index.query(EventFilter.none(), 500, null, lastId);
                if (replay.truncated() || lastId > replay.latestSeq()
                        || index.replayCursorExpired(lastId, replay.epoch())) {
                    sent = replay.latestSeq();
                    write(out, "reset", sent, hub.json(Map.of("epoch", replay.epoch(), "reason", "replay_gap")));
                } else {
                    sent = lastId;
                    for (EventSummary e : replay.items().reversed()) {
                        write(out, "security-event", e.seq(), hub.json(ApiJson.summary(e)));
                        sent = e.seq();
                    }
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

    private static void streamHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        securityHeaders(headers);
        headers.set("Content-Type", "text/event-stream; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Accel-Buffering", "no");
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

    private void dashboard(HttpExchange exchange, String path, WorkspaceSettings settings) throws IOException {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        var query = parseQuery(rawQuery);
        String suffix = rawQuery == null ? "" : "?" + rawQuery;
        boolean oldHome = path.equals("/") || path.equals("/index.html");
        if (oldHome && (query.containsKey("event") || query.containsKey("live")
                || query.containsKey("decision") || query.containsKey("q"))) {
            String id = query.getOrDefault("event", "");
            redirect(exchange, (EventParser.SAFE_ID.matcher(id).matches()
                    ? "/telemetry/events/" + id : "/telemetry") + suffix);
            return;
        }
        String canonical = path.equals("/index.html") ? "/" : stripTrailingSlash(path);
        DashboardPage.Page page = DashboardPage.Page.at(canonical);
        String event = null;
        if (canonical.startsWith("/telemetry/events/")) {
            event = canonical.substring("/telemetry/events/".length());
            if (EventParser.SAFE_ID.matcher(event).matches() && index.find(event) != null) {
                page = DashboardPage.Page.TELEMETRY;
            }
        }
        if (page == null) {
            sendPage(exchange, DashboardPage.missing(settings), "text/html; charset=utf-8", false, 404);
        } else if (!path.equals(canonical)) {
            redirect(exchange, canonical + suffix);
        } else {
            sendPage(exchange, DashboardPage.render(page, config, index, query, event, viva, settings),
                    "text/html; charset=utf-8", false);
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        securityHeaders(exchange.getResponseHeaders());
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(308, -1);
        exchange.close();
    }

    private void sendPage(HttpExchange exchange, String page, String contentType, boolean embedded) throws IOException {
        sendPage(exchange, page, contentType, embedded, 200);
    }

    private void sendPage(HttpExchange exchange, String page, String contentType, boolean embedded, int status) throws IOException {
        byte[] body = page.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        securityHeaders(headers);
        if (embedded) {
            frameHeaders(headers);
        }
        if ("/settings".equals(exchange.getRequestURI().getRawPath())) {
            headers.set("Referrer-Policy", "same-origin");
        }
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            headers.set("Content-Length", String.valueOf(body.length));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
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

    private static void frameHeaders(Headers headers) {
        headers.set("Content-Security-Policy", CSP.replace("frame-ancestors 'none'", "frame-ancestors 'self'"));
        headers.set("X-Frame-Options", "SAMEORIGIN");
        headers.set("Referrer-Policy", "same-origin");
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
