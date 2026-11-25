# Viva notes

**Demonstration.** Build with `mvn clean verify`, show Monitor, Alert, and Block, then open a recorded event and point out the ordered Java frames and the `enforced` field.

**Research question.** Can method order reveal changes invisible to the rules and aggregate stack counts? We hold argument hash, rule score and frame composition constant, then change the order. We also permute the sequence of individually familiar events so that only a transition-based model can see the change.

**Demo the important distinction.** Train the Java audit CLI on `Cache-nominal.jsonl` and score `Cache-sequence.jsonl`. `KNOWN` with `UNSEEN_TRANSITION` means that the individual stack is familiar and its transition from the previous stack is unfamiliar. [Commands](../README.md) · [Results](../reports/jvm-ordered-path-study.md)

**Closest paper.** Feng et al. use native return addresses and program counters. Our JVM adaptation compares entry and exit suffixes of adjacent method stacks captured at selected JDK APIs. An unusual path is advisory evidence. Benign code changes can trigger it, as the drift experiment demonstrates. [Primary paper](https://conferences.computer.org/sp/pdfs/sp/2003/03_02_02.pdf)
