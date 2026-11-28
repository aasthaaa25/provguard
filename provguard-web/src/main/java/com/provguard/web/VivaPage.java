package com.provguard.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.provguard.web.DashboardPage.escape;

final class VivaPage {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> EVENT_FIELDS = List.of("startupEvent", "blockedEvent", "sequenceEvent");
    private static final List<String> STEPS = List.of("Monitor startup", "Enforce policy", "Fit baseline", "Audit sequence");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu · HH:mm:ss 'UTC'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private VivaPage() {}

    static String render(WebConfig config, EventLogIndex index, VivaLab lab) {
        return """
                <section class="viva-hero">
                  <div><p class="eyebrow">UCS553 · JAVA RESEARCH PROJECT</p>
                    <h1>See what your<br><span>Java runtime does.</span></h1>
                    <p>Run a service startup in fresh JVMs. Inspect the calls it makes, see a policy stop a
                      denylisted object, and compare an altered call sequence against the benign baseline.</p>
                    <div class="actions"><a class="button primary" href="#run">Open the live lab</a>
                      <a class="button" href="/runs">Run history</a></div>
                  </div>
                  <div class="viva-diagram" aria-label="Execution flow">
                    <div><b>Application</b><span>Service restores its saved cache</span></div>
                    <div><b>Java agent</b><span>Records call stacks and applies runtime policy</span></div>
                    <div><b>Ordered-path audit</b><span>Compares the test sequence with the baseline</span></div>
                  </div>
                </section>
                <section class="section viva-section" id="run">
                  <div class="section-head"><div><p class="eyebrow">LIVE EXPERIMENT</p><h2>Run the startup lab</h2></div></div>
                """ + (lab == null ? savedEvidence(config, index)
                : "<iframe class=\"viva-frame\" title=\"Live Java experiment and results\" src=\"/viva/panel\"></iframe>")
                + """
                  <div class="workflow-links viva-links">
                    <a href="/method"><strong>From paper to code</strong><span>Feng et al. and our adaptation of ordered method entry and exit.</span></a>
                    <a href="/walkthrough"><strong>Recorded Ant walkthrough</strong><span>Compare a baseline, a benign wrapper, and an alternate launcher.</span></a>
                    <a href="/research"><strong>Ant and Maven benchmark</strong><span>Compare detection, false alarms, and startup-inclusive overhead.</span></a>
                  </div>
                  <details class="workspace-panel viva-terminal"><summary>Reproduce from a terminal</summary>
                    <p>From the repository root with JDK 21:</p>
                    <pre><code>mvn verify
                java -jar provguard-web/target/provguard-web-1.0.0.jar --viva</code></pre>
                    <p>Open <a href="/viva">Viva demo</a> in the dashboard. For a standalone run, pass a fresh directory name:</p>
                    <pre><code>java -cp provguard-agent/target/provguard-agent-1.0.0.jar com.provguard.agent.study.VivaRun provguard-agent/target/provguard-agent-1.0.0.jar provguard-demo-app/target/provguard-demo-app-1.0.0.jar viva-run</code></pre>
                  </details>
                </section>
                """;
    }

    static String panel(VivaLab lab, WorkspaceSettings settings) {
        var status = lab.status();
        String heading = status.running() ? "Java experiment running"
                : status.failed() ? "Run failed. Check the server console and retry."
                : status.completed() == STEPS.size() ? "Run complete" : "Ready to run";
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Live Java experiment</title><link rel="stylesheet" href="/dashboard.css">
                """);
        if (status.running()) {
            html.append("<meta http-equiv=\"refresh\" content=\"1;url=/viva/panel\">");
        }
        html.append("</head><body class=\"viva-panel").append(settings.classes())
                .append("\"><div class=\"viva-control\"><div><strong>")
                .append(heading).append("</strong><p>");
        if (status.elapsedMillis() == 0) {
            html.append("Run four fresh JVM workloads and inspect the resulting evidence.");
        } else {
            html.append(String.format(Locale.ROOT, "%.1f s elapsed · %d of %d stages complete",
                    status.elapsedMillis() / 1_000.0, status.completed(), STEPS.size()));
        }
        html.append("</p></div>");
        if (!status.running()) {
            html.append("<form method=\"post\" action=\"/viva/run\"><input type=\"hidden\" name=\"token\" value=\"")
                    .append(lab.token()).append("\"><button class=\"viva-run\" type=\"submit\">")
                    .append(status.completed() == 0 && !status.failed() ? "Run Java experiment" : "Run again")
                    .append("</button></form>");
        }
        html.append("</div>");
        if (status.running()) {
            html.append("<progress class=\"viva-track\" aria-label=\"Completed experiment stages\" max=\"")
                    .append(STEPS.size()).append("\" value=\"").append(status.completed()).append("\"></progress>");
        }
        html.append("<ol class=\"viva-progress\" aria-label=\"Execution stages\">");
        for (int i = 0; i < STEPS.size(); i++) {
            String state = i < status.completed() ? "Complete"
                    : status.running() && i == status.completed() ? "Running" : "Waiting";
            html.append("<li data-state=\"").append(state).append("\"><b>").append(STEPS.get(i))
                    .append("</b><span>").append(state).append("</span></li>");
        }
        html.append("</ol>");
        if (status.evidence() != null) {
            if (status.running() || status.failed()) {
                html.append("<p>Evidence from the last completed run</p>");
            }
            var evidence = status.evidence();
            html.append(results(evidence.report(), "/viva/evidence/" + evidence.id() + "/"))
                    .append("<div class=\"run-links\"><a target=\"_top\" href=\"/runs/").append(evidence.id())
                    .append("\">Open run analysis</a><a href=\"/runs/").append(evidence.id())
                    .append("/evidence.zip\">Download evidence ZIP</a></div>");
        } else {
            html.append("<p class=\"viva-empty\">The evidence cards and stack links appear here when the run finishes.</p>");
        }
        return html.append("</body></html>").toString();
    }

    static String history(VivaLab lab) {
        var runs = lab == null ? List.<VivaLab.Evidence>of() : lab.history();
        var html = new StringBuilder("""
                <section class="section history-page">
                  <p class="eyebrow">RESEARCH WORKSPACE</p><h1>Run history</h1>
                  <p class="section-intro">Reopen the last eight completed runs from this dashboard session.
                    Download an evidence bundle to keep it for your report.</p>
                  <a class="button primary" href="/viva#run">Open the live lab</a>
                """);
        if (runs.isEmpty()) {
            html.append("<p class=\"capture-note\">Completed Java experiments will appear here.</p>");
        } else {
            html.append("<div class=\"history-list\">");
            for (var run : runs) {
                html.append("""
                        <article class="history-entry workspace-panel">
                          <div><h2>Java startup experiment</h2>%s</div>
                          <div><strong>%d events</strong><span>Startup capture</span></div>
                          <div><strong>%d transitions</strong><span>Unseen in baseline</span></div>
                          <a class="button" href="/runs/%s">Inspect run</a>
                        </article>
                        """.formatted(timestamp(run), run.report().path("startupCount").asInt(),
                        run.report().path("unseenTransitions").asInt(), run.id()));
            }
            html.append("</div>");
        }
        return html.append("</section>").toString();
    }

    static String runAnalysis(VivaLab.Evidence run) {
        var report = run.report();
        int test = report.path("sequenceCount").asInt();
        return String.format(Locale.ROOT, """
                <section class="section run-analysis">
                  <a class="back" href="/runs">Back to run history</a>
                  <p class="eyebrow">CONTROLLED JAVA EXPERIMENT</p><h1>Run analysis</h1>
                  <div class="run-heading"><p>%s · %.1f s elapsed</p>
                    <a class="button primary" href="/runs/%s/evidence.zip">Download evidence ZIP</a></div>
                  <section class="workspace-panel run-comparison">
                    <h2>Same stacks, different order</h2>
                    <p>The profile learned from %d benign events. These two readings describe the separate reordered test run.</p>
                    %s%s
                  </section>
                  <h2 class="run-evidence-title">Event evidence</h2>
                  %s
                  <p class="limit-note">This is a controlled workload. The <a href="/research">Ant and Maven results</a>
                    cover the external application evaluation.</p>
                </section>
                """, timestamp(run), run.elapsedMillis() / 1000.0, run.id(),
                report.path("nominalCount").asInt(),
                comparison("Familiar stack snapshots", report.path("knownStacks").asInt(), test, "snapshots"),
                comparison("Unseen transitions", report.path("unseenTransitions").asInt(), test, "transitions"),
                results(report, "/viva/evidence/" + run.id() + "/"));
    }

    private static String timestamp(VivaLab.Evidence run) {
        return "<time datetime=\"" + run.finishedAt() + "\">" + DATE.format(run.finishedAt()) + "</time>";
    }

    private static String comparison(String label, int count, int total, String kind) {
        return """
                <div class="comparison-row %s">
                  <div><span>%s</span><strong>%d / %d</strong></div>
                  <meter aria-label="%s" min="0" max="%d" value="%d"></meter>
                </div>
                """.formatted(kind, label, count, total, label, total, count);
    }

    private static String savedEvidence(WebConfig config, EventLogIndex index) {
        JsonNode report = config.demo() ? null : readReport(config.eventsFile().resolveSibling("viva-report.json"), index);
        return report == null
                ? "<p class=\"capture-note\">Start the dashboard with <code>--viva</code> to run the experiment here.</p>"
                : "<p class=\"capture-note\">Saved evidence from a completed Java run.</p>" + results(report, "/telemetry/events/");
    }

    static JsonNode readReport(Path file, EventLogIndex index) {
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= 8_192) {
                JsonNode report = JSON.readTree(file.toFile());
                if (report != null && report.isObject() && EVENT_FIELDS.stream().allMatch(field -> {
                    String id = report.path(field).asText();
                    return EventParser.SAFE_ID.matcher(id).matches() && index.find(id) != null;
                })) {
                    return report;
                }
            }
        } catch (IOException unavailable) {
            // A missing or incomplete report is shown as unavailable.
        }
        return null;
    }

    private static String results(JsonNode report, String events) {
        return "<div class=\"viva-evidence\">"
                + card("capture", "Monitor", report.path("startupCount").asInt() + " events",
                        "The startup continued after a rule flagged an operation.", events + report.path("startupEvent").asText(),
                        "Inspect startup stack")
                + card("block", "Enforce", "Blocked", "Block mode stopped deserialization of the denylisted teaching fixture.",
                        events + report.path("blockedEvent").asText(), "Inspect enforced block")
                + card("audit", "Audit", report.path("unseenTransitions").asInt() + " unseen",
                        "The offline audit learned from " + report.path("nominalCount").asInt() + " baseline events. "
                                + report.path("knownStacks").asInt() + " of " + report.path("sequenceCount").asInt()
                                + " test events had familiar stacks.", events + report.path("sequenceEvent").asText(),
                        "Inspect unseen transition")
                + "</div>";
    }

    private static String card(String kind, String title, String value, String description, String link, String label) {
        return """
                <article class="viva-card %s"><p>%s</p><h3>%s</h3><p>%s</p>
                <a class="button" target="_top" href="%s">%s</a></article>
                """.formatted(kind, title, value, description, link, label);
    }

    static String transition(VivaLab.Evidence evidence, String eventId) {
        JsonNode report = evidence.report();
        JsonNode path = report.path("transition");
        if (!eventId.equals(report.path("sequenceEvent").asText()) || !path.isObject()) {
            return "";
        }
        return """
                <section class="workspace-panel viva-terminal">
                  <p class="eyebrow">OFFLINE AUDIT · KNOWN STACK / UNSEEN TRANSITION</p>
                  <h2>What changed between these two calls?</h2>
                  <p>%s to %s · %d shared application frames</p>
                  <dl><dt>Exited methods, innermost first</dt><dd>%s</dd><dt>Entered methods, outermost first</dt><dd>%s</dd></dl>
                </section>
                """.formatted(escape(path.path("previousSink").asText()), escape(path.path("currentSink").asText()),
                path.path("sharedDepth").asInt(), methods(path.path("exits")), methods(path.path("entries")));
    }

    private static String methods(JsonNode names) {
        var methods = new java.util.ArrayList<String>();
        names.forEach(name -> methods.add("<code>" + escape(name.asText()) + "</code>"));
        return methods.isEmpty() ? "None" : String.join("<br>", methods);
    }
}
