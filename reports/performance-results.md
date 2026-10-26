# Performance measurements

Measured on this machine with 12 process calls and 3 startup samples. The median startup sample is reported. These numbers are observations, not a claim that overhead is low.

| Metric | Without agent | With agent | Difference | Percentage overhead |
| --- | ---: | ---: | ---: | ---: |
| Startup median (ms) | 147.5 | 2059.3 | 1911.8 | 1296.4% |
| Average process call (ms) | 87.3 | 98.8 | 11.4 | 13.1% |
| Used heap after the run (bytes) | 2197208 | 9274232 | 7077024 | 322.1% |

Startup includes extracting the bootstrap runtime JAR and installing Byte Buddy advice. The per-call figure is the cost after that work. Monitor, Alert, and Block modes share the same fast path for an allowed echo. Block mode adds a throw only when a rule match is confirmed, which this harness does not trigger.

The bounded queue drops events instead of blocking the application when it is full. That behaviour is covered by `StorageTest` rather than by this process loop, because a short `echo` loop does not fill a 1024-slot queue.
