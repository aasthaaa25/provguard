# Ordered JVM method-path study

JDK 21.0.12.1+1-LTS. Isolation Forest seed 42. Three designed workload variants and twelve fresh instrumented JVMs.
Each fold learns on one nominal workload, sets thresholds on another, and tests the third.
Test labels come from the launch scenario. All cases deserialize the same harmless class.
Reordered pairs share rule score, safe argument hash, method multiset, and aggregate stack features.
Reordered cases swap application-method order inside each stack. The sequence scenario
permutes existing snapshots across events. The per-event denominator includes transitions
left intact by that permutation. Drift adds a legitimate wrapper.
Virtual paths retain the common-prefix depth and the exits and entries between consecutive
observed snapshots on the same thread. Each fixture run starts with an empty snapshot.
A method token is the unqualified method name in this cross-variant study. This deliberately shares
role names across fixture classes. The standalone CLI uses qualified names for same-application audits.
Negative and positive examples are paired by launch iteration and checked before scoring.
Rules use the existing alert threshold of 30. Frequency threshold is 1000 for all captures.
Model thresholds target at most 5% nominal validation false alarms. Score 101 disables alerts.

| Detector | Reordered alerts | Sequence alerts | Nominal false alarms | Drift false alarms | FPR across both benign scenarios |
| --- | ---: | ---: | ---: | ---: | ---: |
| Rules | 0/72 | 0/72 | 0/72 | 0/72 | 0.0% |
| Aggregate Isolation Forest | 0/72 | 0/72 | 0/72 | 0/72 | 0.0% |
| Unordered method profile | 0/72 | 0/72 | 0/72 | 72/72 | 50.0% |
| Ordered method profile | 72/72 | 0/72 | 0/72 | 72/72 | 50.0% |
| Virtual path profile | 72/72 | 54/72 | 0/72 | 36/72 | 25.0% |
| Rules + both path profiles | 72/72 | 54/72 | 0/72 | 72/72 | 50.0% |

| Train | Validate | Test | Aggregate threshold | Unordered threshold | Ordered threshold | Virtual threshold | Ordered reorder alerts | Virtual sequence alerts |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Cache | Import | Replay | 101 | 1 | 1 | 1 | 24 | 18 |
| Import | Replay | Cache | 101 | 1 | 1 | 1 | 24 | 18 |
| Replay | Cache | Import | 101 | 1 | 1 | 1 | 24 | 18 |

Raw events and predictions.csv accompany this report. The repeated observations from each workload
are correlated. The three fixture variants share the serialization harness, method vocabulary and
constructed perturbation. This is a controlled mechanism experiment with a deliberate drift stress
case. Its rates do not estimate performance on production applications.
