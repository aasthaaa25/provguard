# Dataset

Training rows and test rows are split by `path_family`. A family is never copied into both sets. Suspicious families are test-only.

Columns:

`event_id`, `timestamp`, `sink_type`, `stack_depth`, `application_frame_count`, `jdk_frame_count`, `external_library_frame_count`, `unique_package_count`, `reflection_detected`, `argument_length`, `keyword_risk_score`, `path_frequency`, `class_loader_count`, `rule_score`, `path_family`, `label`, `split`

Labels are `BENIGN` and `SUSPICIOUS`. The commands behind the live demos are local `echo` processes. The dataset does not contain exploit payloads.

Generate, train, and evaluate in Java:

```powershell
.\scripts\train-ml.ps1
```

That writes `ml-training/data/controlled-events.csv`, the model resource under `provguard-ml`, and `reports/ml-evaluation.md`. The agent loads the packaged model. `mlUrl` is an optional HTTP scorer; if it is omitted or down, the Java model is used.
