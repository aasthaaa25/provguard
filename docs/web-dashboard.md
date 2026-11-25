# Java-rendered dashboard

After building, run from the repository root:

```text
java -jar provguard-web/target/provguard-web-1.0.0.jar --events events.jsonl
```

Open [the local dashboard](http://127.0.0.1:8765/). Java renders recent events with a decision filter and links to full event records. Refresh after generating events. Use `--demo` for clearly marked synthetic data or `--help` for configuration.

The HTTP API also offers statistics, policies, model metadata, downloads and a live server-sent event stream. The service binds to loopback by default and reads the agent's JSONL file.
