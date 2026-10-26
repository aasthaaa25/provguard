# Class diagram

```mermaid
classDiagram
    class SecurityEvent {
        +UUID eventId
        +SinkType sinkType
        +String pathSignature
        +String argumentHash
        +String redactedArgumentPreview
    }
    class CallFrame
    class RiskResult {
        +int ruleScore
        +Integer mlScore
        +Decision decision
        +boolean enforced
    }
    class RiskRule {
        <<interface>>
        +evaluate(SecurityEvent)
    }
    class RuleBasedRiskAnalyzer
    class HybridRiskAnalyzer
    class BlockingPolicy
    class EventQueue
    class SecurityEventWorker
    class JsonlEventWriter
    class MlScorer {
        <<interface>>
    }
    class ResilientMlScorer
    class IsolationForestScorer
    class RestMlScorer
    class FallbackMlScorer
    class ProvGuardAgent
    class EventPipeline

    SecurityEvent --> CallFrame
    SecurityEvent --> RiskResult
    RuleBasedRiskAnalyzer --> RiskRule
    HybridRiskAnalyzer --> BlockingPolicy
    EventPipeline --> RuleBasedRiskAnalyzer
    EventPipeline --> HybridRiskAnalyzer
    EventPipeline --> EventQueue
    EventPipeline --> MlScorer
    SecurityEventWorker --> JsonlEventWriter
    ResilientMlScorer ..|> MlScorer
    IsolationForestScorer ..|> MlScorer
    RestMlScorer ..|> MlScorer
    FallbackMlScorer ..|> MlScorer
    ResilientMlScorer --> IsolationForestScorer
    ResilientMlScorer --> RestMlScorer
    ResilientMlScorer --> FallbackMlScorer
    ProvGuardAgent --> EventPipeline
    RiskRule <|-- KeywordRule
    RiskRule <|-- DeserializationClassRule
    RiskRule <|-- ReflectionRule
```

`KeywordRule`, `DeserializationClassRule`, and `ReflectionRule` stand in for the full rule set in `provguard-rules`. Each rule returns a score delta. `BlockingPolicy` is the only place that turns those scores into allow, alert, or block. `ResilientMlScorer` is the scorer the agent constructs: an optional REST endpoint first, then the packaged Isolation Forest, then `FallbackMlScorer` when that model file is missing.
