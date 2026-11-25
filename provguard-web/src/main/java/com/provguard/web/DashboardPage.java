package com.provguard.web;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Java-rendered project showcase and read-only event console. */
final class DashboardPage {
    private DashboardPage() {}

    private static final String HEAD = """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="color-scheme" content="dark">
            <link rel="stylesheet" href="/dashboard.css">
            """;

    enum Page {
        HOME("/", "Overview"), WALKTHROUGH("/walkthrough", "Walkthrough"), METHOD("/method", "Method"),
        RESEARCH("/research", "Results"), ENGINEERING("/engineering", "Engineering"), TEAM("/team", "Team"),
        TELEMETRY("/telemetry", "Telemetry");
        final String path;
        final String title;
        Page(String path, String title) { this.path = path; this.title = title; }
        static Page at(String path) {
            for (Page page : values()) {
                if (page.path.equals(path)) return page;
            }
            return null;
        }
    }

    static String render(Page page, WebConfig config, EventLogIndex index, Map<String, String> query, String eventId) {
        String content = switch (page) {
            case HOME -> HOME;
            case WALKTHROUGH -> ResearchShowcase.WALKTHROUGH;
            case METHOD -> ResearchShowcase.METHOD;
            case RESEARCH -> RESULTS;
            case ENGINEERING -> ResearchShowcase.ENGINEERING;
            case TEAM -> TEAM;
            case TELEMETRY -> eventId == null ? telemetry(config, index, query)
                    : "<section class=\"section\"><a class=\"back\" href=\"/telemetry?" + escape(consoleQuery(query))
                    + "\">Back to event console</a><p class=\"eyebrow\">RUNTIME TELEMETRY</p><h1>Event detail</h1>"
                    + detail(index, index.find(eventId)) + "</section>";
        };
        if (page != Page.HOME && eventId == null) {
            content = content.replaceFirst("<h2>", "<h1>").replaceFirst("</h2>", "</h1>");
        }
        return layout(page, eventId == null ? page.title : "Event detail", content);
    }

    static String missing() {
        return layout(null, "Page not found", """
                <section class="section"><p class="eyebrow">404</p><h1>Page not found</h1>
                <p>This page or event is no longer available.</p>
                <a class="button primary" href="/">Back to overview</a>
                <a class="button" href="/telemetry">Open telemetry</a></section>
                """);
    }

    private static String layout(Page current, String title, String content) {
        StringBuilder navigation = new StringBuilder();
        for (Page item : Page.values()) {
            if (item == Page.HOME) continue;
            navigation.append("<a href=\"").append(item.path).append('"');
            if (item == current) navigation.append(" aria-current=\"page\"");
            navigation.append('>').append(item.title).append("</a>");
        }
        return HEAD + "<title>" + escape(title) + " · ProvGuard</title></head><body>"
                + "<a class=\"skip\" href=\"#main\">Skip to content</a><header class=\"top\"><div class=\"shell nav\">"
                + "<a class=\"brand\" href=\"/\"><span class=\"brand-icon\" aria-hidden=\"true\"></span>ProvGuard</a>"
                + "<nav aria-label=\"Primary\">" + navigation + "</nav></div></header>"
                + "<main id=\"main\" class=\"shell" + (current == Page.HOME ? " home" : " inner") + "\">"
                + content + "</main><footer class=\"shell footer\"><a class=\"brand\" href=\"/\">ProvGuard</a>"
                + "<span>UCS553 · Java runtime monitoring research</span>"
                + "<a href=\"https://github.com/aasthaaa25/provguard\">GitHub repository</a></footer></body></html>";
    }

    private static String telemetry(WebConfig config, EventLogIndex index, Map<String, String> query) {
        var snapshot = index.snapshot();
        long flagged = snapshot.stream().filter(EventSummary::flagged).count();
        long blocked = snapshot.stream().filter(EventSummary::blocked).count();
        boolean live = "on".equals(query.get("live"));
        return """
                <section class="section"><div class="section-head"><div><p class="eyebrow">RUNTIME TELEMETRY</p>
                <h2>Event console</h2></div>
                """ + (config.demo()
                ? "<span class=\"state demo\">Demo data · synthetic</span>"
                : "<span class=\"state\"><span class=\"dot\"></span>Agent log</span>")
                + "</div><div class=\"metrics\" aria-label=\"Indexed telemetry totals\">"
                + metric("Indexed events", snapshot.size(), "Records available in this session")
                + metric("Flagged events", flagged, "Alerts and policy matches")
                + metric("Enforced blocks", blocked, "Calls actually stopped") + "</div>" + form(query, live)
                + (live ? "<iframe title=\"Auto-refreshing event list\" src=\"/live?" + escape(filters(query)) + "\"></iframe>"
                : table(index, query, false))
                + "<div class=\"api-links\"><a href=\"/api/export?format=csv&amp;" + escape(filters(query))
                + "\">Export filtered CSV</a><a href=\"/api/stats\">Statistics API</a>"
                + "<a href=\"/api/model\">Model metadata</a><a href=\"/api/policies\">Policy catalog</a></div></section>";
    }

    private static String consoleQuery(Map<String, String> query) {
        return filters(query) + ("on".equals(query.get("live")) ? "&live=on" : "");
    }

    private static final String HOME = """
            <section class="hero">
            <div class="hero-copy"><p class="eyebrow"><span></span> UCS553 · JAVA SECURITY RESEARCH PROJECT</p>
            <h1>Every call leaves <em>a trail.</em></h1>
            <p class="intro">ProvGuard asks whether the route to a sensitive Java call reveals changes that rules and stack counts miss. See the detector work on a real build.</p>
            <div class="actions"><a class="button primary" href="/walkthrough">Explore the Ant example</a><a class="button" href="/telemetry">Explore telemetry</a></div>
            </div>
            <div class="scope" role="img" aria-label="Illustration of JVM stack frames converging on a monitored call">
            <div class="scope-top"><span>JVM / CALL PATH</span><span>STACKWALKER</span></div>
            <div class="radar" aria-hidden="true"><i></i><i></i><i></i><b></b><strong>JVM</strong></div>
            <div class="scope-bottom"><span>ENTRY <b>→</b> FRAME <b>→</b> SINK</span><span>JAVA 21</span></div>
            </div>
            </section><section class="section home-project"><div class="section-head"><div><p class="eyebrow">EXPLORE THE PROJECT</p><h2>From experiment to evidence</h2></div></div><div class="pipeline route-cards">
            <article><h3>Walk a real Ant build</h3><p>Compare a normal launch, a benign change and a simulated deviation using captured JVM frames.</p><a href="/walkthrough">Explore the walkthrough</a></article>
            <article><h3>Understand the detector</h3><p>See the VtPath adaptation, then compare detection and false alarms.</p><a href="/method">Read the method</a> · <a href="/research">See the results</a></article>
            <article><h3>Follow the evidence</h3><p>Inspect actual events or follow how the agent, audit tool and review app fit together.</p><a href="/telemetry">Open telemetry</a> · <a href="/engineering">Explore engineering</a></article></div></section>
            """;

    private static final String RESULTS = """
            <section id="research" class="section">
            <div class="section-head"><div><p class="eyebrow"><span></span> MEASURED RESULTS</p><h2>Where it works. Where it doesn't.</h2></div></div>
            <div class="evidence">
            <article><p class="eyebrow">CONTROLLED JVM STUDY</p><h3>Order reveals new signals.</h3><strong>72 / 72</strong><p>Reordered stacks flagged. The transition profile also flagged 54 of 72 observations in reordered event sequences, and 36 of 72 benign drift observations.</p></article>
            <article><p class="eyebrow">EXTERNAL APPLICATIONS</p><h3>Tested on Ant and Maven.</h3><strong>4 / 4</strong><p>Simulated policy-deviation runs flagged. Two benign Ant workflow runs also triggered alerts. Monitoring added about 164–166% elapsed time on these short, fresh-JVM jobs.</p></article>
            </div>
            <div class="table-wrap benchmark"><table><caption>External evaluation · two fresh runs per condition and application</caption>
            <thead><tr><th scope="col">Application</th><th scope="col">Policy deviations flagged</th><th scope="col">Benign runs flagged</th><th scope="col">Median job time without / with agent</th></tr></thead>
            <tbody><tr><th scope="row">Apache Ant</th><td>2 / 2</td><td>2 / 6</td><td>0.99 s / 2.63 s</td></tr>
            <tr><th scope="row">Apache Maven</th><td>2 / 2</td><td>0 / 6</td><td>2.87 s / 7.57 s</td></tr></tbody>
            </table></div>
            <p class="limit-note">On Ant and Maven, ordered snapshots and transitions produced the same alert counts. The controlled sequence test isolates the added value of transitions. Timing includes cold JVM startup, the job and log flushing. It excludes offline auditing.</p>
            <div class="api-links"><a href="https://github.com/aasthaaa25/provguard/blob/main/docs/research-draft.md">Research draft</a><a href="https://github.com/aasthaaa25/provguard/blob/main/reports/jvm-external-application-study.md">Benchmark report</a><a href="https://conferences.computer.org/sp/pdfs/sp/2003/03_02_02.pdf">Feng et al. · IEEE S&amp;P 2003</a></div>
            </section>
            """;

    private static final String TEAM = """
            <section id="team" class="section team">
            <div class="section-head"><div><p class="eyebrow"><span></span> UCS553 · RESEARCH PROJECT</p><h2>Project team</h2></div></div>
            <div class="pipeline">
            <article><span class="avatar">AT</span><h3>Aastha Tyagi</h3><p>Roll no. 1024160131</p></article>
            <article><span class="avatar">NS</span><h3>Naman Sharma</h3><p>Roll no. 1024160039</p></article>
            <article><span class="avatar">NA</span><h3>Namarata Aneja</h3><p>Roll no. 1024160040</p></article>
            </div>
            </section>
            """;

    static String live(EventLogIndex index, Map<String, String> query) {
        return HEAD + "<title>Live events · ProvGuard</title><meta http-equiv=\"refresh\" content=\"5\"></head><body class=\"embedded\">"
                + table(index, query, true) + "</body></html>";
    }

    private static String metric(String name, long total, String explanation) {
        return "<article><span>" + name + "</span><strong>" + total + "</strong><p>" + explanation + "</p></article>";
    }

    private static String form(Map<String, String> query, boolean live) {
        StringBuilder html = new StringBuilder("""
                <form action="/telemetry" method="get" class="filters">
                <label>Search events<input type="search" name="q" placeholder="Call path, sink or event ID" value="
                """.stripTrailing());
        html.append(escape(query.getOrDefault("q", ""))).append("\"></label><label>Decision<select name=\"decision\">");
        for (String value : new String[]{"", "ALLOW", "ALERT", "BLOCK"}) {
            html.append("<option value=\"").append(value).append("\"")
                    .append(value.equalsIgnoreCase(query.get("decision")) ? " selected" : "").append(">")
                    .append(value.isEmpty() ? "All decisions" : value).append("</option>");
        }
        return html.append("</select></label><label class=\"toggle\"><input type=\"checkbox\" name=\"live\" value=\"on\"")
                .append(live ? " checked" : "").append("> Auto-refresh every 5s</label><button type=\"submit\">Apply</button></form>")
                .toString();
    }

    private static String filters(Map<String, String> query) {
        return "q=" + encode(query.getOrDefault("q", "")) + "&decision=" + encode(query.getOrDefault("decision", ""));
    }

    private static String table(EventLogIndex index, Map<String, String> query, boolean live) {
        Long before = null;
        String cursor = query.getOrDefault("before", "");
        if (cursor.matches("[0-9]{1,18}")) {
            before = Long.parseLong(cursor);
        }
        var page = index.query(EventFilter.fromQuery(query), live ? 8 : 30, live ? null : before, null);
        StringBuilder html = new StringBuilder("""
                <div class="table-wrap"><table><caption>Showing %d of %d matching events</caption>
                <thead><tr><th scope="col">Timestamp</th><th scope="col">Monitored API</th>
                <th scope="col">Outcome</th><th scope="col">Rules</th><th scope="col">ML</th>
                <th scope="col">Call path</th></tr></thead><tbody>
                """.formatted(page.items().size(), page.totalMatching()));
        for (EventSummary event : page.items()) {
            String tone = event.blocked() ? "blocked" : event.flagged() ? "flagged" : "normal";
            html.append("<tr><td>").append(escape(event.timestamp())).append("</td><td>")
                    .append(escape(event.sinkMethod())).append("</td><td><span class=\"outcome ").append(tone).append("\">")
                    .append(escape(event.outcome().replace('_', ' '))).append("</span></td><td>")
                    .append(escape(event.ruleScore())).append("</td><td>").append(escape(event.mlScore()))
                    .append("</td><td><a ").append(live ? "target=\"_top\" " : "").append("href=\"/telemetry/events/")
                    .append(encode(event.id())).append(live ? "?live=on&amp;" : "?")
                    .append(escape(filters(query)))
                    .append("\">").append(escape(event.pathSignature() == null ? "Inspect event" : event.pathSignature()))
                    .append("</a></td></tr>");
        }
        if (page.items().isEmpty()) {
            html.append("<tr><td colspan=\"6\" class=\"empty\">No matching events. Adjust the filters or run the agent demo to generate telemetry.</td></tr>");
        }
        html.append("</tbody></table></div>");
        if (!live && (before != null || page.nextBefore() != null)) {
            html.append("<nav class=\"api-links\" aria-label=\"Event pages\"><a href=\"/telemetry?")
                    .append(escape(filters(query))).append("\">Newest events</a>");
            if (page.nextBefore() != null) {
                html.append("<a href=\"/telemetry?").append(escape(filters(query))).append("&amp;before=")
                        .append(page.nextBefore()).append("\">Older events</a>");
            }
            html.append("</nav>");
        }
        return html.toString();
    }

    private static String detail(EventLogIndex index, EventSummary event) {
        if (event == null) {
            return "<article class=\"event-detail\" id=\"event-detail\"><h3>Event no longer indexed</h3></article>";
        }
        StringBuilder html = new StringBuilder("<article class=\"event-detail\" id=\"event-detail\"><h3>Event details</h3><p>")
                .append(escape(event.id())).append(" · ").append(escape(event.sinkMethod())).append("</p><p>Enforced: ")
                .append(escape(event.enforced())).append(" · Rules: ").append(escape(String.join(", ", event.matchedRules())))
                .append("</p>");
        try {
            var frames = index.parser().executionOrderFrames(index.readRecord(event), event);
            if (frames.isEmpty()) {
                html.append("<p>This record has no captured frames.</p>");
            } else {
                html.append("<ol class=\"frames\" aria-label=\"Application entry to monitored call\">");
                for (var frame : frames) {
                    html.append("<li><code>").append(escape(frame.get("className"))).append('.')
                            .append(escape(frame.get("methodName"))).append("</code> <small>")
                            .append(escape(frame.get("role"))).append("</small></li>");
                }
                html.append("</ol>");
            }
        } catch (IOException | EventLogIndex.SourceChanged failure) {
            html.append("<p>The log changed or could not be read. Reload to try again.</p>");
        }
        html.append("<a href=\"/api/events/")
                .append(encode(event.id())).append("\">View full event record</a></article>");
        return html.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escape(Object value) {
        return value == null ? "—" : value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
