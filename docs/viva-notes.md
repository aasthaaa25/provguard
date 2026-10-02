# How to explain the project

ProvGuard is a Java runtime security-monitoring agent. It instruments process execution and Java deserialization class resolution. On each monitored call it captures the current stack with `StackWalker`, builds a call-path signature, and stores a hash, a length, and a redacted preview of the argument.

A rule engine scores known patterns: command keywords, reflection on the stack, repeated execution, and a deserialization allowlist and denylist. A Java Isolation Forest, trained on controlled call-path features, adds an anomaly score inside the agent. Monitor and Alert modes never stop the application. Block mode throws `ProvGuardBlockedException` only when the rule score is at least 80, or when the rule score is 60 to 79 and the model score is at least 80. The model cannot block on its own.

If the agent, the model, or the log file fails, the target application continues.

Advanced work that is intentionally not in this version: graph neural networks, full taint tracking, Soot or WALA call graphs, dynamic attach, JFR, and complete JNDI coverage.
