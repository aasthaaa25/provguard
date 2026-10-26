# Sequence: monitored process start

```mermaid
sequenceDiagram
    participant App
    participant Advice
    participant Pipeline
    participant Rules
    participant Model
    participant Queue
    participant Worker

    App->>Advice: ProcessBuilder.start()
    Advice->>Pipeline: onProcessBuilderStart
    Pipeline->>Pipeline: recursion guard and StackWalker
    Pipeline->>Rules: deterministic rules
    alt Block mode and rule score >= 80
        Pipeline-->>App: ProvGuardBlockedException
    else Block mode and rule score 60 to 79
        Pipeline->>Model: score with a 200 ms timeout
        alt model score >= 80
            Pipeline-->>App: ProvGuardBlockedException
        else otherwise
            Pipeline->>Queue: offer event
            Pipeline-->>App: return, method continues
        end
    else otherwise
        Pipeline->>Queue: offer event
        Pipeline-->>App: return, method continues
    end
    Worker->>Queue: take event
    Worker->>Model: ML score or ML_UNAVAILABLE
    Worker->>Worker: append events.jsonl
```

The synchronous model call happens only for the 60–79 band in Block mode. Other events are scored on the worker before they are written. The background worker never throws `ProvGuardBlockedException`. If the queue is full, the event is dropped and the application continues.
