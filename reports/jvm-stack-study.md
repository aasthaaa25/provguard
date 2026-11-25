# ProvGuard JVM stack study

JDK 21.0.12.1+1-LTS. Seed 42. Separate entry routes for training, validation and test.
The study checks that no recorded path signature crosses those splits.
Each split is one fresh JVM. All events are from one fixture, one sink and six call depths.
Test positives are harmless denylisted class reads. Both classes use identical test routes.
Thresholds were selected on benign validation to keep the observed false-positive rate at or below 5%.
A threshold of 101 disables alerts for that score. The hybrid retains all rules alerts
and uses the reported threshold only for additional model alerts. Isolation Forest uses no class name,
rule score, label, or call-path signature as input.

| Detector | Threshold | TP | FP | TN | FN | Recall | FPR | Precision |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Rules | 1 | 60 | 0 | 60 | 0 | 100.0% | 0.0% | 100.0% |
| Isolation Forest without stack | 1 | 0 | 0 | 60 | 60 | 0.0% | 0.0% | N/A |
| Isolation Forest with stack | 101 | 0 | 0 | 60 | 60 | 0.0% | 0.0% | N/A |
| Hybrid with stack | 101 | 60 | 0 | 60 | 0 | 100.0% | 0.0% | 100.0% |

This is a controlled policy benchmark. Iterations from one JVM and fixture are correlated.
Results apply to this fixture and do not establish general JVM attack detection performance.
