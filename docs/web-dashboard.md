# Java-rendered dashboard

After building, run from the repository root:

```text
java -jar provguard-web/target/provguard-web-1.0.0.jar --events events.jsonl
```

Open [the local dashboard](http://127.0.0.1:8765/). Java renders the research showcase and recent events. Search or filter by decision, then select a call path to view the event details. Enable **Auto-refresh every 5s** and click **Apply** to refresh the event table in place. Use `--demo` for clearly marked synthetic data or `--help` for configuration.

For a live viva, build with JDK 21 and run from the repository root:

```text
mvn verify
java -jar provguard-web/target/provguard-web-1.0.0.jar --viva
```

Open [Viva demo](http://127.0.0.1:8765/viva), then choose **Run Java experiment**. The browser shows progress as Java monitors startup, enforces a rule on a teaching fixture, trains a baseline, and audits a separate run. Each result links to an actual call stack and full record. The transition evidence shows where execution left one call path and entered the next. The lab uses fixed workloads and is available only with `--viva` on the local dashboard.

**Run history** keeps the last eight successfully completed experiments for the current dashboard session. Run analysis shows the familiar stack and unseen transition counts for the test run. Download **Evidence ZIP** to keep the report, trained profile, and original JSONL files across restarts. **Settings** saves the theme and motion preference in the current browser. The Projector theme uses stronger contrast and larger interface text.

Then open `/walkthrough` for the recorded Ant example. Compare the baseline, benign wrapper, and alternate launcher before opening `/method` for the VtPath explanation. The walkthrough represents the measured study. The event console at `/telemetry` shows the selected log, with a separate label for synthetic demo data. Individual records have shareable URLs at `/telemetry/events/<id>`.

The overview at `/` links to the walkthrough, method, results at `/research`, implementation at `/engineering`, and credits at `/team`.

Clients of `/api/stream` can reconnect with a last event ID. If that cursor cannot be replayed from the retained log within the 500-event replay limit, the server emits a `reset` event with reason `replay_gap`. Reload the event list through `/api/events` before consuming subsequent stream events.

The HTTP API also offers statistics, policies, model metadata, downloads and a live server-sent event stream. The service binds to loopback by default and reads the agent's JSONL file.
