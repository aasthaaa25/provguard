# External Java application study

Parent JDK 21.0.12.1+1-LTS on Windows 11 (amd64). Applications are the stock Apache Ant and Apache Maven CLI entry points.
Each application trains on two fresh nominal JVMs and tests on two separate JVMs per scenario.
The Java harness generates only build configuration. All runs invoke four local, harmless Java
readiness probes. No scenario exercises an exploit. The policy-deviation scenario deliberately uses
an alternate launcher that a hypothetical build policy reserves; the launched operation stays the same.

Nominal and input-change runs use the same task route. Input changes the probe argument.
A benign workflow change wraps the Ant Exec task in Sequential or invokes Maven's same plugin goal directly.
Policy deviation uses Ant's forked Java task instead of Exec, or Maven Antrun instead of Exec Maven Plugin.
Qualified names are fit per application on benign logs only. The virtual audit flags unseen transitions,
including the first observed path after a thread starts. Counts refer to sink events, which are correlated.
A run is flagged if it contains at least one virtual transition alert. Rules use the standard alert threshold.

| Application | Scenario | Events | Rules alerts | Ordered snapshot alerts | Virtual transition alerts | Flagged runs |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Apache Ant | nominal | 16 | 0 | 0 | 0 | 0/2 |
| Apache Ant | input-change | 16 | 0 | 0 | 0 | 0/2 |
| Apache Ant | workflow-change | 16 | 0 | 16 | 16 | 2/2 |
| Apache Ant | policy-deviation | 16 | 0 | 16 | 16 | 2/2 |
| Apache Maven | nominal | 8 | 0 | 0 | 0 | 0/2 |
| Apache Maven | input-change | 8 | 0 | 0 | 0 | 0/2 |
| Apache Maven | workflow-change | 8 | 0 | 0 | 0 | 0/2 |
| Apache Maven | policy-deviation | 16 | 0 | 16 | 16 | 2/2 |

## End-to-end monitoring cost

Three alternating-order, fresh-JVM pairs per application, with plugin resolution completed first.
Values are medians of full child-process wall time in milliseconds, including JVM startup, build tool,
four forked probes, agent startup where applicable, rule/model work, and log flushing.
The audit is offline and excluded from wall-time measurements. These are single-host, short-run timings.

| Application | No agent (ms) | Monitor (ms) | Delta (ms) | Delta |
| --- | ---: | ---: | ---: | ---: |
| Apache Ant | 989.2 | 2629.2 | +1640.0 | +165.8% |
| Apache Maven | 2869.4 | 7572.9 | +4703.5 | +163.9% |

Raw logs, exact generated build inputs, profiles, predictions.csv and timing.csv accompany the report.
Results concern external applications on controlled build jobs, with one monitored API family and
a simulated policy deviation. They do not estimate production attack-detection accuracy.
