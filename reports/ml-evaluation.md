# Isolation Forest evaluation

The model is a Java Isolation Forest trained on benign call-path families only. The test set is a held-out benign family plus suspicious families that were never shown to the model.

- Test rows: 150
- Train families: safe-deserialize, safe-echo, safe-runtime
- Test families: heldout-safe-batch, suspicious-deserialize, suspicious-frequency, suspicious-keyword, suspicious-reflection
- Mean score on held-out benign rows: 51.7
- Mean score on suspicious rows: 94.0
- Recall at score 80: 1.00
- False positive rate at score 80: 0.13

A high anomaly score alerts. It does not block unless the deterministic rule score is already in the high band.

These figures describe this controlled dataset. They are not a claim about zero-day detection or about every Java attack.
