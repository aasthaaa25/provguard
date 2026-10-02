# Architecture

ProvGuard is a Java agent. The fast path runs on the application thread and can block only a confirmed rule match. Detailed logging and anomaly scoring run on one background worker.

```mermaid
flowchart TD
    app[Target Java application]
    agent[ProvGuardAgent.premain]
    buddy[Byte Buddy advice]
    collector[StackWalker and safe metadata]
    rules[Fast rule engine]
    queue[ArrayBlockingQueue]
    worker[Background worker]
    ml[Java Isolation Forest]
    jsonl[events.jsonl]

    app --> buddy
    agent --> buddy
    buddy --> collector
    collector --> rules
    rules -->|allow or alert| app
    rules -->|block in Block mode| blocked[ProvGuardBlockedException]
    rules --> queue
    queue --> worker
    worker --> ml
    worker --> jsonl
    ml -->|unavailable| worker
```

The three instrumented methods are `Runtime.exec`, `ProcessBuilder.start`, and `ObjectInputStream.resolveClass`. A stack trace from `StackWalker` is a call-path trace. It is not a provenance graph and it is not taint tracking.
