# Architecture

The Java premain entry point installs Byte Buddy advice for selected process, deserialization, JNDI and script API calls. The application thread collects StackWalker frames, builds redacted argument metadata and applies deterministic rules. A bounded queue passes telemetry to a background worker.

In Block mode, a rule score of at least 80 can interrupt the call immediately. For scores of 60–79, a separately configured ML endpoint can confirm enforcement with a score of at least 80. The application-thread request has a 200 ms budget. The worker preserves that decision and score in the audit record. The bundled Isolation Forest annotates telemetry without authorizing enforcement.

The log writer emits JSONL. The Java dashboard indexes those records, renders a browser view and exposes a read-only JSON API, exports and an event stream. The research runner fits separate evaluation models using collected JVM events.
