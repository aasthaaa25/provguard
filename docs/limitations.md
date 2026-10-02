# Limitations

ProvGuard is an academic runtime-monitoring project for controlled Java applications. It monitors selected high-risk Java APIs. It does not cover every Java framework, every process-creation mechanism, every deserialization gadget chain, every JNDI implementation, or every remote-code-execution bug.

The stack-based execution path is not dynamic taint tracking and it is not a full application provenance graph. The Isolation Forest score reflects the controlled training families. It can mark a benign path as unusual and it can miss a suspicious path. Blocking is limited to high-confidence deterministic rules. A high anomaly score by itself alerts and logs. It does not stop the application.

Native process creation outside the JVM is not visible. One JDK `Runtime.exec` call can delegate to another overload; ProvGuard records the outermost call. `ProcessBuilder.start` on this JDK delegates into a private overload and can also allocate a `ProcessBuilder` while executing a string `Runtime.exec`, so both sinks can appear for one demo action.
