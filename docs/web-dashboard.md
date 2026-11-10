# ProvGuard web dashboard

A browser dashboard for the events the ProvGuard agent already records. It is a read-only view. It does not start, stop, or configure the agent, and it adds no second detector.

```text
JVM + agent  ->  event pipeline  ->  events.jsonl  ->  provguard-web (Java)  ->  browser
 (unchanged)      (unchanged)         (append only)     read-only HTTP API      HTML + CSS + JS
```

## Build and run (Windows PowerShell)

Requirements are the same as the rest of the project: JDK 21 or newer and Maven 3.9 or newer.

```powershell
# 1. Build every module, including provguard-web
mvn -DskipTests package

# 2. Run a safe demo with the agent attached. It writes events.jsonl in the repository root.
.\scripts\run-demo.ps1 -Mode ALERT -Demo suspicious
.\scripts\run-demo.ps1 -Mode BLOCK -Demo blocked        # optional: produces a real enforced block

# 3. Start the dashboard service on that log
.\scripts\run-dashboard.ps1
#    same thing without the script:
java -jar provguard-web\target\provguard-web-1.0.0.jar --events events.jsonl

# 4. Open the dashboard
Start-Process http://127.0.0.1:8765/
```

The dashboard keeps watching the file. Start it before or after the agent; run more demos and new events appear without a reload. To preview the interface without an agent, run `.\scripts\run-dashboard.ps1 -Demo`. Demo mode serves invented events and shows **DEMO DATA — NOT LIVE TELEMETRY** on every page.

Run tests (all modules, including the new one):

```powershell
mvn test
mvn -pl provguard-web -am test      # just the dashboard and what it depends on
```

### Options

| Option | Default | Meaning |
| --- | --- | --- |
| `--events <file>` | `events.jsonl` | JSONL log the agent writes. Must match the agent's `events=` argument. |
| `--reports <dir>` | `reports` | Directory holding `ml-evaluation.md`. |
| `--host <address>` | `127.0.0.1` | Bind address. Anything that is not loopback needs `--allow-remote`. |
| `--port <port>` | `8765` | Bind port. `0` picks a free one. |
| `--allow-remote` | off | Permits a non-loopback bind and disables the Host-header check. Not recommended; there is no authentication. |
| `--demo` | off | Serve labelled synthetic events instead of a log. |
| `--max-events <n>` | `50000` | Most recent events kept in the in-memory index. |
| `--poll-ms <n>` | `1000` | How often the log is checked. |
| `--active-window <sec>` | `300` | Log silence after which the agent is shown as "quiet". |

## How events reach the website

1. The agent appends one JSON object per monitored call to `events.jsonl`. Nothing about this changed.
2. `provguard-web` reads the file only. It remembers a byte offset per event and a small summary, so the whole file is never held in memory, and details are read from the original line on demand.
3. Rotation is handled: a smaller file, a replaced file (checked by file identity and a fingerprint of the first bytes), or a deleted file resets the index and tells the browser to reload. A half-written last line waits for its newline. Malformed lines and duplicate event ids are skipped and counted.
4. The browser opens a Server-Sent Events stream (`/api/stream`). If the stream cannot be kept, it falls back to polling `/api/events?after=<seq>` every 3 seconds and retries the stream. SSE was chosen because `HttpServer` handles it reliably and it resumes from `Last-Event-ID`. Both paths de-duplicate by event id, so reconnects never double-count.

## API

All responses are JSON. Only `GET` and `HEAD` are accepted.

| Endpoint | Purpose |
| --- | --- |
| `GET /api/health` | Backend status, inferred agent status, log source state and counters. |
| `GET /api/events` | Newest-first summaries. Query: `limit` (max 500), `before`, `after` (sequence numbers), `q`, `sink`, `severity`, `decision`, `outcome`, `mode`, `from`, `to`. |
| `GET /api/events/{id}` | One event: whitelisted fields, frames in execution order, rule and ML scores kept apart. |
| `GET /api/stats` | Totals, breakdowns, timeline, score histograms, all computed from indexed records. Same filters as `/api/events`. |
| `GET /api/stream` | Server-Sent Events: `hello`, `security-event`, `reset`, `overflow`. |
| `GET /api/policies` | Rules, thresholds, decision matrix, modes, monitored sinks. Read-only. |
| `GET /api/model` | Packaged model status and the parsed offline evaluation report. |
| `GET /api/export?format=csv\|jsonl` | Streamed copy of up to 100,000 matching events, oldest first. Same filters. |

### Event ids and the outcome field

The agent writes a random UUID in `eventId`. The bridge uses it as the id. If a record has none (or an unsafe value) the id is `derived-` plus 16 hex characters of the SHA-256 of the raw line, which is stable for that line.

`outcome` is derived from the recorded fields and never from the score:

| `outcome` | Recorded fields | Meaning |
| --- | --- | --- |
| `blocked` | `enforced = true` | The agent stopped the call. |
| `policy_block_not_enforced` | `policyDecision = BLOCK`, `enforced = false` | The policy would block, but the mode (Monitor or Alert) does not stop calls. The call ran. |
| `flagged` | `decision = ALERT` | An alert was recorded. The call ran. |
| `allowed` | `decision = ALLOW` | No alert. |

## Pages

| Page | What is real |
| --- | --- |
| Overview | Event, flagged, blocked and would-block-not-enforced counts; activity chart; sink breakdown; recent events; health. All computed from records. Empty log shows start-up instructions. |
| Live monitor | Live table with search, sink, severity, decision and outcome filters, visible filter chips, pause/resume, clear view, selection, connection state and last update time. Pausing and clearing change only the display. |
| Call-path explorer | The selected event's recorded frames as a vertical rail, entry first, sink last. Frame inspector, collapsible JDK frames, keyboard arrows. Reflection frames are flagged using the agent's own test. The page states that it is a stack trace, not data-flow or taint analysis. |
| Event details | Every recorded field, rule score and ML score in separate cards, decision versus enforcement, explicit "Not recorded" for process information. |
| ML analytics | Packaged model status, live score distribution, live counts, and the offline report parsed from `reports/ml-evaluation.md`, labelled as offline. |
| Policies | The 13 rules, thresholds, a decision matrix produced by calling the real `BlockingPolicy`, mode semantics, monitored sinks, agent arguments. Read-only. |
| Audit logs | Server-side search and filters, date range, load-older paging, event access, export of loaded rows (CSV or JSON) or all matches (CSV or JSONL). |
| Settings and health | Backend status, inferred agent status, mode last recorded, log counters, skipped-line list, model status, frontend configuration. |

## Backend health versus agent health

The backend is the Java service behind the page. If it is up, the page loads. The agent runs inside your application's JVM and exposes no heartbeat, so the dashboard cannot confirm it is running. It reports only what the log shows: recent activity, quiet, no events, or no log file, and labels this as inferred. The mode shown is the `mode` of the latest event, not a live setting.

## Security

- Binds to `127.0.0.1` by default. Other addresses need `--allow-remote`.
- Read-only API. No shell access, no arbitrary file access, no management endpoints. Static files come only from the bundled `/web` resources through a strict name whitelist.
- The Host header must be `localhost`, `127.0.0.1` or `[::1]`, which blocks DNS-rebinding from other web pages.
- `Content-Security-Policy` allows scripts and styles from the same origin only. No external fonts, scripts or requests.
- Event text is inserted with `textContent`/text nodes only. The frontend contains no `innerHTML`, `insertAdjacentHTML`, `document.write` or `eval`.
- The log's full path is not sent to the browser, only the file name. The API returns the agent's own redacted 20-character argument preview on the detail endpoint only, never in lists.
- CSV export neutralises cells that begin with `=`, `+`, `-` or `@`.

## What is not available

- **Agent liveness.** No heartbeat exists. Status is inferred from the log.
- **Process information.** Application name, process id and user are not logged by the agent.
- **Live configuration.** Mode, allow/deny lists and thresholds the agent started with are not logged. Defaults are shown on the Policies page, labelled as defaults.
- **Controls.** The dashboard cannot change the mode, rules or enforcement. Block mode, pausing and clearing are not wired to anything in the agent.
- **Data-flow or taint tracking.** Not implemented in ProvGuard, so not shown.
- **Live precision or recall.** The log has no ground-truth labels. Only the offline report's own figures are shown, and precision and F1 are shown as not reported because the file does not contain them.
- **Whether the model or a remote scoring endpoint was used.** Only each event's `modelStatus` is recorded.
- **JNDI and script-engine sinks.** Defined in the model but not monitored in this version.

## Changes to the existing agent

None. No Java source in an existing module was modified. The only edit outside `provguard-web` is one `<module>` line in the root `pom.xml`, plus this documentation and `scripts/run-dashboard.ps1`.
