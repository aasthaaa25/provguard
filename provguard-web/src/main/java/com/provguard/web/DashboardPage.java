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
        HOME("/", "Overview"), VIVA("/viva", "Live lab"), RUNS("/runs", "Run history"),
        TELEMETRY("/telemetry", "Telemetry"), CAPTURE("/capture", "Capture"),
        WALKTHROUGH("/walkthrough", "Walkthrough"), METHOD("/method", "Method"),
        RESEARCH("/research", "Results"), ENGINEERING("/engineering", "Engineering"), TEAM("/team", "Team"),
        SETTINGS("/settings", "Settings");
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

    static String render(Page page, WebConfig config, EventLogIndex index, Map<String, String> query, String eventId,
                         VivaLab lab, WorkspaceSettings settings) {
        String content = switch (page) {
            case HOME -> overview(config, index);
            case VIVA -> VivaPage.render(config, index, lab);
            case RUNS -> VivaPage.history(lab);
            case SETTINGS -> settings.render("1".equals(query.get("saved")));
            case CAPTURE -> capture(config, index);
            case WALKTHROUGH -> ResearchShowcase.WALKTHROUGH;
            case METHOD -> ResearchShowcase.METHOD;
            case RESEARCH -> RESULTS;
            case ENGINEERING -> ResearchShowcase.ENGINEERING;
            case TEAM -> TEAM;
            case TELEMETRY -> eventId == null ? telemetry(config, index, query)
                    : "<section class=\"section\"><a class=\"back\" href=\"/telemetry?" + escape(consoleQuery(query))
                    + "\">Back to event console</a><p class=\"eyebrow\">RUNTIME TELEMETRY</p><h1>Event detail</h1>"
                    + detail(index, index.find(eventId), "/api/events/" + encode(eventId)) + "</section>";
        };
        if (page != Page.HOME && page != Page.VIVA && page != Page.RUNS && page != Page.SETTINGS && eventId == null) {
            content = content.replaceFirst("<h2>", "<h1>").replaceFirst("</h2>", "</h1>");
        }
        return layout(page, eventId == null ? page.title : "Event detail", content, source(config, index), settings);
    }

    static String runAnalysis(VivaLab.Evidence run, WorkspaceSettings settings) {
        return layout(Page.RUNS, "Run analysis", VivaPage.runAnalysis(run), "Local Java experiment", settings);
    }

    static String vivaEvidence(VivaLab.Evidence evidence, EventSummary event, WorkspaceSettings settings) {
        return layout(Page.RUNS, "Experiment evidence",
                "<section class=\"section\"><a class=\"back\" href=\"/runs/" + evidence.id() + "\">Back to run analysis</a>"
                + "<p class=\"eyebrow\">FRESH JVM EXPERIMENT</p><h1>Event evidence</h1>"
                + VivaPage.transition(evidence, event.id()) + detail(evidence.index(), event,
                        "/viva/evidence/" + evidence.id() + "/" + encode(event.id()) + "/record") + "</section>",
                "Local Java experiment", settings);
    }

    static String missing(WorkspaceSettings settings) {
        return layout(null, "Page not found", """
                <section class="section"><p class="eyebrow">404</p><h1>Page not found</h1>
                <p>This page or event is no longer available.</p>
                <a class="button primary" href="/">Back to overview</a>
                <a class="button" href="/telemetry">Open telemetry</a></section>
                """, "Local workspace", settings);
    }

    private static String layout(Page current, String title, String content, String source, WorkspaceSettings settings) {
        StringBuilder navigation = new StringBuilder();
        for (Page item : Page.values()) {
            if (item == Page.SETTINGS) continue;
            if (item == Page.HOME || item == Page.WALKTHROUGH) {
                navigation.append(item == Page.HOME ? "<span>Workspace</span>" : "<span>Research</span>");
            }
            navigation.append("<a href=\"").append(item.path).append('"');
            if (item == current) navigation.append(" aria-current=\"page\"");
            navigation.append('>').append(item.title).append("</a>");
        }
        return HEAD + "<title>" + escape(title) + " · ProvGuard</title></head><body class=\"workspace"
                + settings.classes() + "\">"
                + "<a class=\"skip\" href=\"#main\">Skip to content</a><aside class=\"workspace-side\">"
                + "<a class=\"brand\" href=\"/\"><span class=\"brand-icon\" aria-hidden=\"true\"></span>ProvGuard</a>"
                + "<nav aria-label=\"Primary\">" + navigation + "</nav>"
                + "<div class=\"workspace-source\">" + escape(source) + "</div></aside>"
                + "<div class=\"workspace-main\"><header class=\"workspace-top\"><span>Workspace / <strong>" + escape(title)
                + "</strong></span><div class=\"top-actions\"><form action=\"/telemetry\" role=\"search\"><input name=\"q\" type=\"search\""
                + " aria-label=\"Search telemetry\" placeholder=\"Search events\"><button type=\"submit\">Search</button></form>"
                + "<a class=\"settings-link\" href=\"/settings\"" + (current == Page.SETTINGS ? " aria-current=\"page\"" : "")
                + ">Settings</a></div></header>"
                + "<main id=\"main\" class=\"shell" + (current == Page.HOME ? " home" : " inner") + "\">"
                + content + "</main><footer class=\"shell footer\"><a class=\"brand\" href=\"/\">ProvGuard</a>"
                + "<span>UCS553 · Java runtime monitoring research</span>"
                + "<a href=\"https://github.com/aasthaaa25/provguard\">GitHub repository</a></footer></div></body></html>";
    }

    private static String source(WebConfig config, EventLogIndex index) {
        if (config.demo()) {
            return "Demo data · synthetic";
        }
        return index.status().exists() ? "Agent log connected" : "Waiting for agent log";
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
                + "</div><p class=\"section-intro\">Live agent telemetry shows rules and advisory model scores. "
                + "The ordered-path detector analyzes saved logs offline.</p>"
                + "<div class=\"metrics\" aria-label=\"Indexed telemetry totals\">"
                + metric("Indexed events", snapshot.size(), "Records available in this session")
                + metric("Flagged events", flagged, "Alerts and policy matches")
                + metric("Enforced blocks", blocked, "Calls actually stopped") + "</div>" + form(query, live)
                + (live ? "<iframe title=\"Auto-refreshing event list\" src=\"/live?" + escape(filters(query)) + "\"></iframe>"
                : table(index, query, false, 30, true))
                + "<div class=\"api-links\"><a href=\"/api/export?format=csv&amp;" + escape(filters(query))
                + "\">Export filtered CSV</a><a href=\"/api/stats\">Statistics API</a>"
                + "<a href=\"/api/model\">Model metadata</a><a href=\"/api/policies\">Policy catalog</a></div></section>";
    }

    private static String consoleQuery(Map<String, String> query) {
        if ("on".equals(query.get("live"))) {
            return filters(query) + "&live=on";
        }
        Long before = beforeCursor(query);
        return filters(query) + (before == null ? "" : "&before=" + before);
    }

    private static Long beforeCursor(Map<String, String> query) {
        String before = query.getOrDefault("before", "");
        return before.matches("[0-9]{1,18}") ? Long.valueOf(before) : null;
    }

    private static String overview(WebConfig config, EventLogIndex index) {
        var events = index.snapshot();
        long blocked = events.stream().filter(EventSummary::blocked).count();
        long flagged = events.stream().filter(event -> event.flagged() && !event.blocked()).count();
        long allowed = events.stream().filter(event -> EventSummary.OUTCOME_ALLOWED.equals(event.outcome())).count();
        String latest = events.isEmpty()
                ? "<p>Start a monitored application to see its latest call path here.</p>"
                : latestEvent(events.getLast());
        return """
                <section class="overview-hero">
                  <div><p class="eyebrow">UCS553 RESEARCH PROJECT</p>
                  <h1>JVM activity, in view.</h1>
                  <p>Inspect monitored Java calls, follow their execution paths, and review the evidence behind each decision.</p>
                  <div class="actions"><a class="button primary" href="/viva">Open viva demo</a>
                  <a class="button" href="/telemetry">Open telemetry</a>
                  <a class="button" href="/capture">Show the Java run</a></div></div>
                  <article class="latest-event"><h2>Latest captured call</h2>
                """ + latest + """
                  </article>
                </section><section class="metrics overview-metrics" aria-label="Workspace totals">
                """ + metric("Indexed events", events.size(), source(config, index))
                + metric("Allowed", allowed, "Recorded as allowed")
                + metric("Flagged", flagged, "Recorded without an enforced block")
                + metric("Enforced blocks", blocked, "Calls actually stopped")
                + """
                </section><div class="overview-grid">
                  <section class="workspace-panel"><div class="panel-head"><h2>Recent events</h2>
                  <a href="/telemetry">All events</a></div>
                """ + table(index, Map.of(), false, 5, false) + """
                  </section><section class="workspace-panel outcomes">
                  <div class="panel-head"><h2>Event outcomes</h2></div>
                """ + outcome("Allowed", allowed, events.size(), "allowed")
                + outcome("Flagged", flagged, events.size(), "flagged")
                + outcome("Enforced blocks", blocked, events.size(), "blocked")
                + outcome("Other", events.size() - allowed - flagged - blocked, events.size(), "other")
                + """
                  </section>
                </div>
                <section class="workspace-panel overview-links"><div class="panel-head"><h2>Research and workflow</h2></div>
                  <div class="workflow-links">
                    <a href="/walkthrough"><strong>Ant walkthrough</strong><span>Compare the three recorded launch paths.</span></a>
                    <a href="/method"><strong>Detection method</strong><span>See how the offline audit compares adjacent stacks.</span></a>
                    <a href="/research"><strong>Benchmark results</strong><span>Review detection, false alarms, and overhead.</span></a>
                  </div>
                </section>
                """;
    }

    private static String latestEvent(EventSummary event) {
        return "<p class=\"latest-api\">" + escape(event.sinkMethod()) + "</p><code>"
                + escape(event.pathSignature()) + "</code><a href=\"/telemetry/events/" + encode(event.id())
                + "\">Inspect latest event</a>";
    }

    private static String outcome(String label, long count, int total, String style) {
        return "<div class=\"outcome-meter " + style + "\"><div><span>" + label + "</span><strong>" + count
                + "</strong></div><meter aria-label=\"" + label + "\" min=\"0\" max=\"" + Math.max(1, total)
                + "\" value=\"" + count + "\"></meter></div>";
    }

    private static String capture(WebConfig config, EventLogIndex index) {
        var query = Map.of("q", "SafeDemoApplication", "mode", "MONITOR");
        var events = index.query(EventFilter.fromQuery(query), 12, null, null);
        String evidence;
        if (config.demo() || events.items().isEmpty()) {
            evidence = "<p class=\"capture-note\">This workspace has no captured Monitor-mode events from the Java example yet. "
                    + "Run the commands below to populate it.</p>";
        } else {
            var event = events.items().getFirst();
            evidence = "<section class=\"workspace-panel execution-evidence\" aria-label=\"Captured Java evidence\">"
                    + "<div class=\"panel-head\"><h2>Captured Java evidence</h2><a href=\"/telemetry/events/"
                    + encode(event.id()) + "?q=SafeDemoApplication&amp;mode=MONITOR\">Inspect latest call</a></div>"
                    + table(index, query, false, 12, false)
                    + "</section>";
        }
        return """
            <section class="section capture-page">
              <div class="section-head"><div><p class="eyebrow">JAVA EXECUTION WALKTHROUGH</p><h2>Follow a startup check</h2></div></div>
              <p class="section-intro">A service can start a helper process and reload saved state at launch. This small Java example models that workflow with local echo commands and a saved Java object.</p>
              <div class="execution-flow">
                <article><h3>Run the application</h3><p>SafeDemoApplication runs local process checks and reads a saved object.</p></article>
                <article><h3>Capture the calls</h3><p>The Java agent records process and deserialization calls in the log.</p></article>
                <article><h3>Inspect the evidence</h3><p>Open a recorded call to show the stack, matched rules, and outcome.</p></article>
              </div>
            """ + evidence + """
              <p class="capture-note">Monitor mode lets the checks finish and records the rule verdict. An alert can appear during a normal startup check. Use the Enforced field to show what the agent actually stopped.</p>
              <h2 class="capture-commands">Run it yourself</h2>
              <p class="section-intro">Run these commands from the repository root with JDK 21.</p>
              <div class="capture-grid">
                <article class="workspace-panel capture-step"><h3>Build the project</h3><pre><code>mvn clean verify</code></pre></article>
                <article class="workspace-panel capture-step"><h3>Run the safe demo in Monitor mode</h3>
                  <pre><code>java "-javaagent:provguard-agent/target/provguard-agent-1.0.0.jar=mode=MONITOR;events=events.jsonl" -cp provguard-demo-app/target/provguard-demo-app-1.0.0.jar com.provguard.demo.safe.SafeDemoApplication</code></pre></article>
                <article class="workspace-panel capture-step"><h3>Serve the recorded log</h3>
                  <pre><code>java -jar provguard-web/target/provguard-web-1.0.0.jar --events events.jsonl</code></pre>
                  <a class="button primary" href="/telemetry">Open telemetry</a></article>
              </div>
            </section>
            """;
    }

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
            <p class="limit-note">On Ant and Maven, ordered snapshots and transitions produced the same alert counts. The controlled sequence test isolates the added value of transitions. Timing includes cold JVM startup, the job and log flushing. It excludes offline auditing. The bundled BETH model reached the high-score threshold on all 72 benign Ant and Maven events in this evaluation.</p>
            <div class="api-links"><a href="https://github.com/aasthaaa25/provguard/blob/main/docs/research-draft.md">Research draft</a><a href="https://github.com/aasthaaa25/provguard/blob/main/reports/jvm-external-application-study.md">Benchmark report</a><a href="https://conferences.computer.org/sp/pdfs/sp/2003/03_02_02.pdf">Feng et al. · IEEE S&amp;P 2003</a></div>
            </section>
            """;

    private static final String TEAM = """
            <section id="team" class="section team">
            <div class="section-head"><div><p class="eyebrow"><span></span> UCS553 · RESEARCH PROJECT</p><h2>Project team</h2></div></div>
            <div class="pipeline">
            <article><span class="avatar">AT</span><h3>Aastha Tyagi</h3><p>Roll no. 1024160131</p></article>
            <article><span class="avatar">NS</span><h3>Naman Sharma</h3><p>Roll no. 1024160039</p></article>
            <article><span class="avatar">NA</span><h3>Namrata Aneja</h3><p>Roll no. 1024160040</p></article>
            </div>
            </section>
            """;

    static String live(EventLogIndex index, Map<String, String> query, WorkspaceSettings settings) {
        return HEAD + "<title>Live events · ProvGuard</title><meta http-equiv=\"refresh\" content=\"5\"></head><body class=\"embedded"
                + settings.classes() + "\">"
                + table(index, query, true, 8, false) + "</body></html>";
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
        html.append("</select></label>");
        if (query.containsKey("mode")) {
            html.append("<input type=\"hidden\" name=\"mode\" value=\"").append(escape(query.get("mode"))).append("\">");
        }
        return html.append("<label class=\"toggle\"><input type=\"checkbox\" name=\"live\" value=\"on\"")
                .append(live ? " checked" : "").append("> Auto-refresh every 5s</label><button type=\"submit\">Apply</button></form>")
                .toString();
    }

    private static String filters(Map<String, String> query) {
        return "q=" + encode(query.getOrDefault("q", "")) + "&decision=" + encode(query.getOrDefault("decision", ""))
                + (query.containsKey("mode") ? "&mode=" + encode(query.get("mode")) : "");
    }

    private static String table(EventLogIndex index, Map<String, String> query, boolean live, int limit, boolean paginate) {
        Long before = live ? null : beforeCursor(query);
        var page = index.query(EventFilter.fromQuery(query), limit, before, null);
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
                    .append(encode(event.id())).append('?')
                    .append(escape(live ? filters(query) + "&live=on" : consoleQuery(query)))
                    .append("\">").append(escape(event.pathSignature() == null ? "Inspect event" : event.pathSignature()))
                    .append("</a></td></tr>");
        }
        if (page.items().isEmpty()) {
            html.append("<tr><td colspan=\"6\" class=\"empty\">No matching events. Adjust the filters or run the agent demo to generate telemetry.</td></tr>");
        }
        html.append("</tbody></table></div>");
        if (paginate && (before != null || page.nextBefore() != null)) {
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

    private static String detail(EventLogIndex index, EventSummary event, String fullRecordUrl) {
        if (event == null) {
            return "<article class=\"event-detail\" id=\"event-detail\"><h3>Event no longer indexed</h3></article>";
        }
        StringBuilder html = new StringBuilder("<div class=\"inspector\"><article class=\"event-detail\" id=\"event-detail\">"
                + "<h2>Call stack</h2><p class=\"inspector-api\">")
                .append(escape(event.sinkMethod())).append("</p>");
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
        html.append("</article><aside class=\"workspace-panel inspect-meta\"><h2>Event evidence</h2><dl>")
                .append(evidenceField("Event ID", event.id())).append(evidenceField("Recorded at", event.timestamp()))
                .append(evidenceField("Outcome", event.outcome().replace('_', ' ')))
                .append(evidenceField("Enforced", event.enforced())).append(evidenceField("Rules score", event.ruleScore()))
                .append(evidenceField("Advisory ML score", event.mlScore()))
                .append(evidenceField("Matched rules", event.matchedRules().isEmpty()
                        ? "None recorded" : String.join(", ", event.matchedRules())))
                .append("</dl><a href=\"").append(escape(fullRecordUrl))
                .append("\">View full event record</a></aside></div>");
        return html.toString();
    }

    private static String evidenceField(String label, Object value) {
        return "<dt>" + label + "</dt><dd>" + escape(value) + "</dd>";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String escape(Object value) {
        return value == null ? "—" : value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
