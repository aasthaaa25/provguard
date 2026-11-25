# Datasets

The Java research runner collects JVM telemetry through the actual agent. Its harmless in-memory deserialization workload uses separate routes for training, validation and test. In the test route, benign objects and an inert denylisted marker run through the same call paths. See the [research draft](research-draft.md).

The separate BETH baseline uses the frozen `ml-training/data/beth-sample.csv` derived from Highnam et al.'s Linux host logs. The sample contains 2,240 benign training rows, 680 benign test rows and 500 suspicious test rows. The trainer adds repeated copies of four benign JVM vectors. The split is by family. The literal host field includes `ubuntu` on both sides. Linux values proxy certain JVM features, so the test measures performance on the BETH sample. [BETH paper](https://ceur-ws.org/Vol-3095/paper1.pdf)
