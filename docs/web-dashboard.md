# Java-rendered dashboard

After building, run from the repository root:

```text
java -jar provguard-web/target/provguard-web-1.0.0.jar --events events.jsonl
```

Open [the local dashboard](http://127.0.0.1:8765/). Java renders the research showcase and recent events. Search or filter by decision, then select a call path to view the event details. Enable **Auto-refresh every 5s** and click **Apply** to refresh the event table in place. Use `--demo` for clearly marked synthetic data or `--help` for configuration.

For the viva, start at `/walkthrough` with the recorded Ant example. Compare the baseline, benign wrapper, and alternate launcher before opening `/method` for the VtPath explanation. The walkthrough represents the measured study. The event console at `/telemetry` shows the selected log, with a separate label for synthetic demo data. Individual records have shareable URLs at `/telemetry/events/<id>`.

The overview at `/` links to the walkthrough, method, results at `/research`, implementation at `/engineering`, and credits at `/team`.

The HTTP API also offers statistics, policies, model metadata, downloads and a live server-sent event stream. The service binds to loopback by default and reads the agent's JSONL file.
