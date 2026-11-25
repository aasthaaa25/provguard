package com.provguard.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Server-rendered dashboard. The read-only JSON and event-stream endpoints remain available. */
final class DashboardPage {
    private DashboardPage() {}

    static String render(WebConfig config, EventLogIndex index, Map<String, String> query) {
        var page = index.query(EventFilter.fromQuery(query), 50, null, null);
        StringBuilder body = new StringBuilder("""
                <!doctype html>
                <html lang="en"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>ProvGuard dashboard</title>
                <main><h1>ProvGuard</h1>
                """);
        body.append(config.demo()
                ? "<p><strong>Demo data.</strong> Synthetic events.</p>"
                : "<p>Read-only JVM telemetry.</p>");
        body.append("""
                <nav><a href="/">Refresh events</a> |
                <a href="/api/health">Health</a> |
                <a href="/api/stats">Statistics</a> |
                <a href="/api/policies">Policies</a> |
                <a href="/api/model">Model</a> |
                <a href="/api/export">Export events</a></nav>
                <form action="/" method="get">
                <label>Filter by decision <select name="decision">
                """);
        String decision = query.get("decision");
        for (String value : new String[]{"", "ALLOW", "ALERT", "BLOCK"}) {
            body.append("<option value=\"").append(value).append("\"")
                    .append(value.equals(decision) ? " selected" : "").append(">")
                    .append(value.isEmpty() ? "All" : value).append("</option>");
        }
        body.append("""
                </select></label> <button type="submit">Apply</button></form>
                <h2>Recent events</h2>
                <table><caption>Showing %d of %d indexed matches</caption>
                <thead><tr><th scope="col">Timestamp</th><th scope="col">API</th>
                <th scope="col">Outcome</th><th scope="col">Rules</th><th scope="col">ML</th>
                <th scope="col">Call path</th></tr></thead><tbody>
                """.formatted(page.items().size(), page.totalMatching()));
        for (EventSummary event : page.items()) {
            String detail = URLEncoder.encode(event.id(), StandardCharsets.UTF_8);
            body.append("<tr><td>").append(escape(event.timestamp())).append("</td><td>")
                    .append(escape(event.sinkMethod())).append("</td><td>").append(escape(event.outcome()))
                    .append("</td><td>").append(escape(event.ruleScore())).append("</td><td>")
                    .append(escape(event.mlScore())).append("</td><td><a href=\"/api/events/").append(detail)
                    .append("\">").append(escape(event.pathSignature())).append("</a></td></tr>\n");
        }
        return body.append("</tbody></table></main></html>").toString();
    }

    private static String escape(Object value) {
        return value == null ? "—" : value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
