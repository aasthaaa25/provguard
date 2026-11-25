# ProvGuard

ProvGuard is a Java 21 runtime monitoring and research prototype. A Byte Buddy agent captures active JVM stack frames with StackWalker at selected API calls, evaluates deterministic rules, and records audit events. The bundled Java Isolation Forest provides advisory telemetry.

The primary research reference is Feng et al., *Anomaly Detection Using Call Stack Information*, IEEE Symposium on Security and Privacy, 2003. Their detector extracts abstract paths from native return addresses. ProvGuard records JVM frames and evaluates the contribution of stack features in a controlled Java benchmark. [Paper](https://conferences.computer.org/sp/pdfs/sp/2003/03_02_02.pdf)

## JavaFX case review

```text
mvn -pl provguard-desktop compile javafx:run
```

Create and edit cases, import the agent's event log into SQLite, search the case list, and review recent actions. [UCS553 syllabus map and viva walkthrough](docs/java-syllabus-viva.md)

## Build and demonstrate

Use JDK 21 or newer and Maven 3.9 or newer. From the repository root:

```text
mvn clean verify
java "-javaagent:provguard-agent/target/provguard-agent-1.0.0.jar=mode=MONITOR;events=events.jsonl" -cp provguard-demo-app/target/provguard-demo-app-1.0.0.jar com.provguard.demo.safe.SafeDemoApplication
java "-javaagent:provguard-agent/target/provguard-agent-1.0.0.jar=mode=ALERT;events=events.jsonl" -cp provguard-demo-app/target/provguard-demo-app-1.0.0.jar com.provguard.demo.suspicious.SuspiciousPathDemo
java "-javaagent:provguard-agent/target/provguard-agent-1.0.0.jar=mode=BLOCK;events=events.jsonl" -cp provguard-demo-app/target/provguard-demo-app-1.0.0.jar com.provguard.demo.safe.DeserializationDemo blocked
java "-javaagent:provguard-agent/target/provguard-agent-1.0.0.jar=mode=BLOCK;events=events.jsonl" -cp provguard-demo-app/target/provguard-demo-app-1.0.0.jar com.provguard.demo.safe.ScriptDemo blocked
java -jar provguard-web/target/provguard-web-1.0.0.jar --events events.jsonl
```

Open [the local dashboard](http://127.0.0.1:8765/). Java renders the page and the event list updates on refresh. The read-only API retains its event stream, statistics, event details, and export. The script demo uses an inert Java engine to exercise a real JDK eval hook on stock Java 21.

## Reproduce the research

After building, run the ordered-path study with a fresh output directory. It launches 12 instrumented JVMs and writes logs, per-event predictions and a results table.

```text
java -cp provguard-agent/target/provguard-agent-1.0.0.jar com.provguard.agent.study.OrderedPathStudy provguard-agent/target/provguard-agent-1.0.0.jar provguard-demo-app/target/provguard-demo-app-1.0.0.jar ordered-study
```

To audit a particular application, collect logs from its known benign runs. Use one JVM run per file. The JVM agent's `appPackages` setting identifies the packages treated as application frames. Run the Java CLI from the repository root with your log paths:

```text
java -cp provguard-agent/target/provguard-agent-1.0.0.jar com.provguard.agent.study.OrderedPathProfile train profile.json benign.jsonl
java -cp provguard-agent/target/provguard-agent-1.0.0.jar com.provguard.agent.study.OrderedPathProfile score profile.json evaluation.jsonl
```

The CLI reports baseline membership for each snapshot and consecutive same-thread transition. The audit reads logs offline and has no enforcement authority. For a quick demonstration after running the study, use `ordered-study/Cache-nominal.jsonl` for training and `ordered-study/Cache-sequence.jsonl` for scoring. Look for `KNOWN` snapshots with `UNSEEN_TRANSITION`.

The prior class-policy study and BETH benchmark can be run separately:

```text
java -cp provguard-agent/target/provguard-agent-1.0.0.jar com.provguard.agent.study.StackStudy provguard-agent/target/provguard-agent-1.0.0.jar provguard-demo-app/target/provguard-demo-app-1.0.0.jar jvm-study-run
java -cp provguard-agent/target/provguard-agent-1.0.0.jar com.provguard.ml.IsolationForestTrainer
```

The second command regenerates the separate BETH baseline report and packaged model source. [Research draft](docs/research-draft.md) · [Ordered path results](reports/jvm-ordered-path-study.md) · [Class-policy pilot](reports/jvm-stack-study.md) · [BETH baseline](reports/ml-evaluation.md)

### Test released Java applications

Build the project, then provide the directories of an Apache Ant installation and an Apache Maven installation. The measured study used Ant 1.10.18 and Maven 3.9.9. Use a fresh output directory and keep the machine otherwise idle during timing.

```text
java -cp provguard-agent/target/provguard-agent-1.0.0.jar com.provguard.agent.study.ExternalApplicationStudy provguard-agent/target/provguard-agent-1.0.0.jar provguard-demo-app/target/provguard-demo-app-1.0.0.jar ANT_HOME MAVEN_HOME external-study
```

Replace `ANT_HOME` and `MAVEN_HOME` with their installation paths and quote paths containing spaces. The Java harness generates the build configurations, primes Maven plugins using the configured Maven repositories, and runs Maven offline during evaluation. The child JVM inherits an explicitly configured Java trust store from the runner if one is needed. Outputs include the generated inputs, baseline profiles, agent logs, predictions, and timing samples. [External application study](reports/jvm-external-application-study.md)

## Enforcement

- Monitor and Alert modes allow the monitored call to proceed. Block mode can interrupt a call with a rule score of at least 80.
- A rule score of 60–79 can enforce only when an explicitly configured ML endpoint confirms it with a score of at least 80 within the application-thread budget. The bundled BETH model has advisory authority only.
- The audit field `enforced` records whether ProvGuard interrupted the call. Asynchronous scoring can indicate a hypothetical policy block.
- ProvGuard monitors selected methods under Runtime, ProcessBuilder, ObjectInputStream, InitialContext and AbstractScriptEngine. Coverage depends on which methods the workload actually calls.

The implementation and runnable study are Java. Maven XML is build configuration. The browser receives standard HTML generated by Java. [Architecture](docs/architecture.md) · [Dashboard](docs/web-dashboard.md)
