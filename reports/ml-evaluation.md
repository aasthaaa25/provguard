# Isolation Forest evaluation

The model is a Java Isolation Forest trained on benign rows from the BETH host-log dataset published on Kaggle as `katehighnam/beth-dataset` (https://www.kaggle.com/datasets/katehighnam/beth-dataset). Training rows are `sus=0` and `evil=0` process events. The test set holds out other benign hosts plus `evil=1` events that were never shown to the model. The four safe call shapes recorded from the ProvGuard demos are included in training so those JVM stacks stay in the benign region.

The `evil` and `sus` columns are labels only. Features are the stack-address list, userspace pointer count, other stack entries, argument-value length, distinct argument names, event name, return value, user id, and process name.

- Sample file: `ml-training/data/beth-sample.csv`
- Test rows: 1180
- Train families: beth-ip-10-100-1-120, beth-ip-10-100-1-173, beth-ip-10-100-1-28, beth-ip-10-100-1-34, beth-ip-10-100-1-55, beth-ip-10-100-1-57, beth-ip-10-100-1-79, beth-ubuntu, safe-deserialize, safe-echo, safe-runtime
- Test families: beth-evil, beth-test-benign, beth-val-ip-10-100-1-129, beth-val-ip-10-100-1-165, beth-val-ip-10-100-1-169, beth-val-ubuntu
- Mean score on held-out benign rows: 30.0
- Mean score on suspicious rows: 98.6
- Recall at score 80: 0.99
- False positive rate at score 80: 0.05

A high anomaly score alerts. It does not block unless the deterministic rule score is already in the high band.

These figures describe this BETH sample and the recorded safe demo shapes. They are not a claim about zero-day detection or about every Java attack.
