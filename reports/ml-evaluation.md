# Isolation Forest evaluation

The Java Isolation Forest is trained on benign rows drawn from `katehighnam/beth-dataset` and four repeated benign JVM demo vectors. Test rows come from BETH families excluded from training. The host field can overlap across splits.

The `evil` and `sus` columns are labels only. Features are the stack-address list, userspace pointer count, other stack entries, argument-value length, distinct argument names, event name, return value, user id, and process name.

- Sample file: `ml-training/data/beth-sample.csv`
- Test rows: 1180
- Train families: beth-ip-10-100-1-120, beth-ip-10-100-1-173, beth-ip-10-100-1-28, beth-ip-10-100-1-34, beth-ip-10-100-1-55, beth-ip-10-100-1-57, beth-ip-10-100-1-79, beth-ubuntu, safe-deserialize, safe-echo, safe-runtime
- Test families: beth-evil, beth-test-benign, beth-val-ip-10-100-1-129, beth-val-ip-10-100-1-165, beth-val-ip-10-100-1-169, beth-val-ubuntu
- Mean score on held-out benign rows: 30.1
- Mean score on suspicious rows: 98.6
- Recall at score 80: 99.40% (497/500)
- False positive rate at score 80: 5.59% (38/680)
- Precision at score 80: 92.90% (497/535)
- F1 at score 80: 96.04%

The test measures ranking on this Linux host-log sample. Its inputs are proxies for JVM features. The bundled model provides advisory telemetry and cannot authorize an enforced block.
