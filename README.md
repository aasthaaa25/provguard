# ProvGuard

ProvGuard is a JDK 21 Java agent that monitors three high-risk APIs, records a `StackWalker` call-path trace, scores the call with deterministic rules, and can attach an Isolation Forest anomaly score. Monitor and Alert modes never stop the application. Block mode throws `ProvGuardBlockedException` only after a high-confidence rule match. A model score by itself never blocks. If the agent, the log, or the model fails, the target application continues.

The call-path trace is a stack trace. It is not a provenance graph and it is not taint tracking.

## What is implemented

- `premain` agent, shaded JAR, Byte Buddy advice
- All `Runtime.exec` overloads, `ProcessBuilder.start`, and `ObjectInputStream.resolveClass`
- Call-path signature, SHA-256, argument length, and a 20-character redacted preview
- Rule engine: keywords, command shape, long arguments, special characters, reflection, unknown path, frequency, deserialization allowlist and denylist, external frames, missing application frames
- Monitor, Alert, and Block modes
- ThreadLocal recursion guard, bounded queue, one background worker, JSONL log
- Java Isolation Forest trained only on benign call-path families and packaged inside the agent
- Second-JVM integration tests and a measured startup / process-call report
- Architecture, class, and sequence diagrams

JNDI coverage, dashboards, ONNX, dynamic attach, taint tracking, and graph models are future work. See `docs/limitations.md`.

## Requirements

- JDK 21 or newer. This tree was built and run on JDK 26 with `--release 21`.
- Maven 3.9 or newer

## Build and test

```powershell
mvn test
mvn verify
```

`mvn verify` runs the unit tests and launches a second JVM with `-javaagent` for the safe, alert, and block demos.

The agent JAR is `provguard-agent\target\provguard-agent-1.0.0.jar`.

## Run a demo

```powershell
.\scripts\run-demo.ps1 -Mode MONITOR -Demo safe
.\scripts\run-demo.ps1 -Mode ALERT -Demo suspicious
.\scripts\run-demo.ps1 -Mode BLOCK -Demo blocked
```

Agent arguments are semicolon-separated:

```text
-javaagent:provguard-agent\target\provguard-agent-1.0.0.jar=mode=BLOCK;events=events.jsonl;verbose=true
```

## Machine learning

The agent scores events with the Isolation Forest class `IsolationForestModel`. The packaged model is `provguard-ml/src/main/resources/com/provguard/ml/isolation-forest.json`. Retrain it with:

```powershell
.\scripts\train-ml.ps1
```

Training families and test families do not overlap. Suspicious families are test-only. The evaluation write-up is `reports/ml-evaluation.md`.

`mlUrl` is optional. When it is set, the agent asks that HTTP endpoint first and uses the packaged Java model if the endpoint is down. A missing model records `ML_UNAVAILABLE` and the rules decide.

## Performance

```powershell
.\scripts\measure-performance.ps1
```

The script writes `reports/performance-results.md` from the runs on this machine. Use those numbers. Do not describe the overhead as low unless the report says so.

## Blocking policy

| Condition | Decision |
| --- | --- |
| Rule score >= 80 | Block in Block mode |
| Rule score 60–79 and ML score >= 80 | Block in Block mode |
| Rule score 30–79 without that ML confirmation | Alert |
| ML score high and rule score below 60 | Alert, never block |
| Rule score below 30 and ML score low | Allow |

A confirmed block is thrown on the application thread before the monitored method runs. The background worker never throws it.

## Layout

```text
provguard-core       domain model and safe argument metadata
provguard-rules      deterministic rules and blocking policy
provguard-storage    queue, worker, JSONL, CSV report
provguard-ml         feature vector, Java Isolation Forest, optional REST client
provguard-agent      premain, Byte Buddy advice, fast path
provguard-demo-app   local echo and deserialization demos
provguard-it         second-JVM agent tests
docs                 architecture, diagrams, tests, limits
```

## Design

Strategy: `RuleBasedRiskAnalyzer`, `MlRiskAnalyzer`, and `HybridRiskAnalyzer`. Factory: `SecurityEventFactory`. Builder: `SecurityEvent.Builder`. Observer: JSONL writer, console reporter, and alert reporter.

Diagrams: `docs/architecture.md`, `docs/class-diagram.md`, `docs/sequence-diagram.md`. Test mapping: `docs/test-cases.md`. Short explanation: `docs/viva-notes.md`.
