# Test cases

| Check | Where it is covered |
| --- | --- |
| Agent starts with `premain` | `AgentAttachmentIT.safeDemoIsMonitoredAndDoesNotBlock` |
| Target application runs with the agent | same test, exit code 0 |
| `ProcessBuilder.start` is monitored | JSONL contains `PROCESS_BUILDER_START` |
| `Runtime.exec` is monitored | JSONL contains `RUNTIME_EXEC` |
| `ObjectInputStream.resolveClass` is monitored | JSONL contains `DESERIALIZATION_RESOLVE_CLASS` |
| Call-path signature | `SecurityEventTest` |
| Raw secret is not stored | `ArgumentMetadataTest` and the safe-demo JSONL check |
| Argument hash and redacted preview | `ArgumentMetadataTest` |
| Keyword, command, reflection, frequency, deserialization rules | `RuleEngineTest` |
| Monitor mode does not block | `EventPipelineTest` and `AgentAttachmentIT` |
| Alert mode does not block | `AgentAttachmentIT.alertModeDoesNotBlockTheSuspiciousDemo` |
| Block mode blocks the denylist class | `AgentAttachmentIT.blockModeDeniesTheDenylistClassOnly` |
| ML score alone does not block | `BlockingPolicyTest.modelAloneNeverBlocks` |
| Java Isolation Forest ranks suspicious families higher | `IsolationForestTest.suspiciousFamiliesScoreHigherThanTheHeldOutBenignFamily` |
| ML failure uses rules | `MlScorerTest.unreachableServiceFallsBack` |
| Recursion guard | `EventPipelineTest.recursionGuardSkipsNestedProcessing` |
| Full queue does not throw | `StorageTest.fullQueueDropsTheEventAndDoesNotThrow` |
| JSONL write | `StorageTest.jsonlWriterOmitsRawSecretsAndReportReadsTheFile` |
| Invalid configuration fails open | `EventPipelineTest.invalidModeFallsOpenToMonitorDefaults` |

Run `mvn verify` for the unit tests and the second-JVM integration tests.
