# Ordered Java stack paths and virtual transitions for runtime audit

## Abstract

ProvGuard captures Java application frames at monitored JDK APIs. We test whether method order adds information beyond rules, aggregate stack features, and unordered method membership. In three designed fixture variants, the ordered snapshot and transition profiles each flagged all 72 reordered stacks. The transition profile also flagged 54 of 72 observations in a trace with permuted events and familiar individual stacks. It flagged none of 72 nominal events and 36 of 72 benign drift events. The Java runner publishes raw events, fold assignments, and per-event predictions.

## Background and contribution

Feng et al.'s VtPath learns from native return addresses and program counters. It compares two successive system-call stacks, finds their common prefix, and records the intervening exits and entries. ProvGuard adapts that operation to ordered Java method frames at instrumented JDK API boundaries. It retains sink identities, shared-prefix length, and ordered suffixes. Java method identities replace native addresses. The prototype omits program-counter checks, the original recursion handling, and an explicit program-end event. Its observations cover selected JVM calls. [Feng et al., IEEE S&P 2003](https://conferences.computer.org/sp/pdfs/sp/2003/03_02_02.pdf)

The aggregate comparison uses the Java implementation of Isolation Forest. The same project contains a separate BETH experiment for historical context. No BETH data or bundled model score is used to fit or evaluate the ordered study. [Liu et al., IEEE ICDM 2008](https://doi.org/10.1109/ICDM.2008.17) · [Highnam et al., CAMLIS 2021](https://ceur-ws.org/Vol-3095/paper1.pdf)

## Method

Three fixture classes, Cache, Import, and Replay, exercise the same harmless serialization sink. Each class runs four scenarios in separate instrumented JDK 21 processes. Each process emits 24 observations, spanning four recursion depths.

| Scenario | Treatment |
| --- | --- |
| Nominal | Use the expected method order and cycle through the four depths. |
| Reordered stack | Swap two nested fixture methods within each observation. |
| Reordered events | Permute the depth cycle while keeping the same multiset of individual snapshots. |
| Benign drift | Insert a legitimate wrapper into the nominal route. |

We rotate the workload held out for testing across three folds. A fold learns from nominal observations in one workload, sets thresholds on nominal observations from another, and tests all four scenarios in the third. Launch configuration supplies labels. The runner verifies that paired nominal and reordered-stack observations have equal argument hashes, rule scores, unordered method tokens, and aggregate feature vectors. It also checks that the nominal and reordered-event traces have identical method-snapshot multisets. Unqualified method names align the designed roles across fixture classes. The application audit CLI uses qualified method names.

The comparison includes the agent rules at their existing alert threshold, Isolation Forest over aggregate stack features, an unordered method profile, an exact ordered snapshot profile, a virtual transition profile, and the logical union of rules and both ordered profiles. Profile thresholds target at most 5% observed false alarms on nominal validation data. The study reports each perturbation separately. The reordered-event denominator includes some transitions left unchanged by the permutation.

## Results

The rules, aggregate Isolation Forest, and unordered profile flagged zero reordered stacks and zero reordered events. The ordered snapshot profile flagged 72/72 reordered stacks and accepted all 72 individually familiar snapshots in the reordered-event scenario. The virtual transition profile flagged 54/72 observations in that sequence and 72/72 reordered stacks. The permutation leaves the initial event and returns to the start of the depth cycle familiar. All methods had zero nominal false alarms. The drift wrapper triggered 72/72 false alarms for exact ordered and unordered snapshots, and 36/72 for virtual transitions. Adding the snapshot detector to the virtual detector preserved the measured detection counts and raised drift false alarms from 36 to 72. [Per-fold counts and thresholds](../reports/jvm-ordered-path-study.md)

The [earlier class-policy pilot](../reports/jvm-stack-study.md) provides a complementary result. When safe and denylisted classes share the same stack route, the deterministic denylist detects the policy marker and stack aggregates contribute no additional detections. Together the two studies show which signal is useful under each controlled comparison.

## External application evaluation

We instrumented the released Apache Ant 1.10.18 and Apache Maven 3.9.9 CLI entry points with the same agent. The Java runner generates build jobs that all invoke the same harmless Java probe four times. Each tool has its own qualified path profile trained on two nominal JVMs. We then evaluated two fresh JVMs per scenario: nominal, changed input, benign workflow change, and a simulated deviation from an explicitly chosen launch-route policy. Ant's workflow change nests Exec under Sequential and its deviation substitutes the forked Java task. Maven's workflow change directly invokes the same Exec plugin goals and its deviation runs the probe through AntRun. This design compares released applications while holding probe functionality constant. [Ant Exec](https://ant.apache.org/manual/Tasks/exec.html) · [Ant Java](https://ant.apache.org/manual/Tasks/java.html) · [Maven Exec](https://www.mojohaus.org/exec-maven-plugin/exec-mojo.html) · [Maven AntRun](https://maven.apache.org/plugins/maven-antrun-plugin/)

The [external study report](../reports/jvm-external-application-study.md) includes rule, ordered snapshot, and virtual transition alerts for each condition. A flagged run contains at least one virtual transition alert. The runner also measures three alternating-order pairs of fresh processes with and without monitoring, after Maven plugins are available offline. The measure includes JVM startup, agent setup, workload execution, and log flush. Offline auditing is outside that timer.

The transition profile flagged both policy-deviation runs for each tool and neither nominal nor changed-input run. It flagged both benign Ant wrapper runs and neither benign Maven direct-invocation run. The ordered snapshot profile produced the same counts on both tools. Rules flagged no events. As a separate diagnostic, the bundled BETH anomaly score was at least 80 on all 104 held-out events, including all 72 benign events. Event counts differ because some launch routes emit both Runtime.exec and ProcessBuilder.start observations for the same probe. The run-level counts avoid treating those observations as independent probes.

Median end-to-end cost increased from 989.2 to 2,629.2 ms for Ant and from 2,869.4 to 7,572.9 ms for Maven. The measured overhead was substantial on these short, cold-start workloads. These totals support keeping transition alerts advisory and evaluating longer-running applications separately.

## Reproducibility and scope

Run the Java study using the [repository instructions](../README.md). The output includes the exact scenario split, raw JSONL logs, and CSV predictions joined by event ID. The audit CLI learns from one or more benign logs and evaluates a separate log in file order, with thread-local transition tracking. Use one JVM run per input file. Missing observations, queue drops, version changes, and benign variant paths can change profile decisions.

The fixture variants share methods and a serialization harness. Observations within a JVM are correlated, and the 72 events in a scenario do not constitute 72 independent workloads. The external applications add evidence on released implementations, with controlled jobs in a single build-tool domain and one monitored API family. The deviation labels describe launch policy rather than real attacks. The sample sizes and single-host timing limit generalization to other applications, versions, JDKs, and deployment environments.
