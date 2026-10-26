# Dataset

The Isolation Forest is trained on the BETH host-log dataset from Kaggle: [katehighnam/beth-dataset](https://www.kaggle.com/datasets/katehighnam/beth-dataset). BETH is labelled Linux process activity from several hosts. Training uses events with `sus=0` and `evil=0`. The test set uses benign events from hosts that were not in training, plus `evil=1` events. Those labels are not features.

`scripts/sample-beth.py` reads `labelled_training_data.csv`, `labelled_validation_data.csv`, and `labelled_testing_data.csv` and writes `ml-training/data/beth-sample.csv`. The checked-in sample is 3,420 rows: 2,240 benign training events, 680 held-out benign events, and 500 evil events. The four safe call shapes recorded from the ProvGuard demos are added at train time so those JVM stacks stay in the benign region.

Each BETH row contributes the stack-address list length, the userspace pointer count, the other stack entries, the length of argument values, the number of distinct argument names, the event name, the return value, the user id, and the process name. JDK frame count is 0 on these rows because the logs are not Java stacks. Class-loader count is fixed at 2, the value observed on the live demos.

Training rows and test rows are split by `path_family`. A family is never copied into both sets. Suspicious families are test-only.

The feature table written next to the model uses these columns:

`event_id`, `timestamp`, `sink_type`, `stack_depth`, `application_frame_count`, `jdk_frame_count`, `external_library_frame_count`, `unique_package_count`, `reflection_detected`, `argument_length`, `keyword_risk_score`, `path_frequency`, `class_loader_count`, `rule_score`, `path_family`, `label`, `split`

Labels are `BENIGN` and `SUSPICIOUS`.

Generate, train, and evaluate in Java:

```powershell
.\scripts\train-ml.ps1
```

That writes `ml-training/data/beth-features.csv`, the model resource under `provguard-ml`, and `reports/ml-evaluation.md`. The agent loads the packaged model. `mlUrl` is an optional HTTP scorer; if it is omitted or down, the Java model is used.
