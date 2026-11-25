package com.provguard.web;

/** Presentation of measured research results, separate from live log data. */
final class ResearchShowcase {
    private ResearchShowcase() {}

    static final String WALKTHROUGH = """
            <section id="walkthrough" class="section walkthrough">
              <div class="section-head"><div><p class="eyebrow"><span></span> THE LIVE DEMO STORY</p>
              <h2>Same job. Different route.</h2></div><span class="state">Recorded Apache Ant study</span></div>
              <p class="section-intro">Imagine a CI build that launches a tiny Java program to check the JVM is ready. We ran that exact job on stock Apache Ant. Choose a scenario to see what ProvGuard observed, and why a flag still needs a reviewer.</p>
              <fieldset class="scenario">
                <legend class="sr-only">Choose an Apache Ant scenario</legend>
                <input class="sr-only" type="radio" name="scenario" id="case-normal" checked>
                <input class="sr-only" type="radio" name="scenario" id="case-drift">
                <input class="sr-only" type="radio" name="scenario" id="case-policy">
                <div class="scenario-tabs">
                  <label for="case-normal"><span>01</span> Baseline run</label>
                  <label for="case-drift"><span>02</span> Benign wrapper</label>
                  <label for="case-policy"><span>03</span> Alternate launcher</label>
                </div>
                <div class="case-panels">
                  <article class="case-panel normal-case">
                    <div class="trace"><p class="eyebrow">SELECTED CAPTURED FRAMES · ENTRY TO SINK</p>
                      <div class="trace-step"><span>ANT TASK</span><code>ExecTask.execute</code></div>
                      <div class="trace-step"><span>EXECUTION</span><code>Execute.launch</code></div>
                      <div class="trace-step sink"><span>MONITORED API</span><code>Runtime.exec</code></div>
                    </div>
                    <div class="verdict"><p class="state">Known transition</p><h3>The expected build route</h3>
                      <p>Ant's Exec task launches the probe. The ordered path and same-thread transition match the profile learned from benign builds.</p>
                      <strong>0 / 16</strong><span>held-out events flagged across two runs</span>
                    </div>
                  </article>
                  <article class="case-panel drift-case">
                    <div class="trace"><p class="eyebrow">SELECTED CAPTURED FRAMES · ENTRY TO SINK</p>
                      <div class="trace-step changed"><span>NEW WRAPPER</span><code>Sequential.execute</code></div>
                      <div class="trace-step"><span>ANT TASK</span><code>ExecTask.execute</code></div>
                      <div class="trace-step"><span>EXECUTION</span><code>Execute.launch</code></div>
                      <div class="trace-step sink"><span>MONITORED API</span><code>Runtime.exec</code></div>
                    </div>
                    <div class="verdict"><p class="state demo">Unseen transition · benign drift</p><h3>A harmless workflow edit</h3>
                      <p>A developer adds a Sequential wrapper. The same probe runs successfully, but an extra frame changes the observed paths. These alerts are false alarms against the study labels.</p>
                      <strong>16 / 16</strong><span>benign events flagged across two runs</span>
                    </div>
                  </article>
                  <article class="case-panel policy-case">
                    <div class="trace"><p class="eyebrow">SELECTED CAPTURED FRAMES · ENTRY TO SINK</p>
                      <div class="trace-step changed"><span>ALTERNATE TASK</span><code>Java.execute</code></div>
                      <div class="trace-step changed"><span>FORK</span><code>Java.fork</code></div>
                      <div class="trace-step"><span>EXECUTION</span><code>Execute.launch</code></div>
                      <div class="trace-step sink"><span>MONITORED API</span><code>Runtime.exec</code></div>
                    </div>
                    <div class="verdict"><p class="state demo">Unseen transition · policy deviation</p><h3>A launcher outside the baseline</h3>
                      <p>Ant's forked Java task runs the identical harmless probe through another route. The simulated policy expects Exec, so the unseen path goes to a reviewer.</p>
                      <strong>16 / 16</strong><span>policy-deviation events flagged across two runs</span>
                    </div>
                  </article>
                </div>
              </fieldset>
              <div class="walk-footer"><span>All three builds completed the same readiness check.</span>
                <a href="https://github.com/aasthaaa25/provguard/blob/main/reports/jvm-external-application-study.md">Read the Ant and Maven benchmark</a></div>
            </section>
            """;

    static final String METHOD = """
            <section id="method" class="section">
              <div class="section-head"><div><p class="eyebrow"><span></span> PAPER TO IMPLEMENTATION</p>
              <h2>What we took from VtPath</h2></div></div>
              <div class="paper-grid">
                <article class="paper-note"><span class="eyebrow">IEEE SYMPOSIUM ON SECURITY AND PRIVACY · 2003</span>
                  <h3>Anomaly Detection Using Call Stack Information</h3>
                  <p>Feng et al. compare successive system-call stacks using native return addresses and a program counter. Their virtual path describes the exits and entries after the stacks' common prefix.</p>
                  <a href="https://conferences.computer.org/sp/pdfs/sp/2003/03_02_02.pdf">Read the original paper</a>
                </article>
                <article class="paper-note adapted"><span class="eyebrow">PROVGUARD · JVM ADAPTATION</span>
                  <h3>Ordered methods at JDK boundaries</h3>
                  <p><code>StackWalker</code> captures Java frames. The audit compares consecutive observations on the same thread, keeps the shared-prefix depth, and learns ordered exits and entries from benign logs.</p>
                  <p class="constraint">Evidence is advisory. The Java adaptation omits native program-counter checks and the paper's recursion handling.</p>
                  <a href="https://github.com/aasthaaa25/provguard/blob/main/provguard-agent/src/main/java/com/provguard/agent/study/OrderedPathProfile.java">Inspect the Java implementation</a>
                </article>
              </div>
              <div class="transition">
                <p class="eyebrow">HOW THE DETECTOR THINKS · SIMPLIFIED EXAMPLE</p>
                <div class="transition-grid">
                  <div><span>Previous stack</span><code><b>Build.main</b> → <b>Target.run</b> → ExecTask.execute → Runtime.exec</code></div>
                  <div><span>Current stack</span><code><b>Build.main</b> → <b>Target.run</b> → Java.execute → Runtime.exec</code></div>
                  <div><span>Virtual transition</span><code>Keep 2 shared frames · exit prior suffix · enter current suffix</code></div>
                </div>
                <p>In the controlled sequence test, each individual stack was familiar. Comparing adjacent observations flagged 54 of 72 events that snapshot matching missed.</p>
              </div>
            </section>
            """;

    static final String ENGINEERING = """
            <section id="engineering" class="section">
              <div class="section-head"><div><p class="eyebrow"><span></span> WHAT WE BUILT</p><h2>From runtime to reviewer</h2></div></div>
              <div class="engineering-list">
                <details open><summary>Runtime agent <span>Java 21 · Byte Buddy · StackWalker</span></summary><p>Instruments selected process launches, deserialization, JNDI lookups, and script evaluation. Captures ordered frames and writes structured JSONL. Monitor, Alert, and Block are policy modes, with enforcement recorded separately.</p></details>
                <details><summary>Detection and decision <span>Rules · Isolation Forest · path profiles</span></summary><p>The deterministic rules and advisory Isolation Forest score are part of agent telemetry. The offline Java audit learns benign ordered stacks and same-thread transitions. An unfamiliar path supplies evidence for review.</p></details>
                <details><summary>Case review workflow <span>JavaFX · SQLite · JDBC</span></summary><p>Import agent logs into the desktop application, search cases, record notes, and update review status. Transactional import skips duplicate event IDs and preserves existing notes.</p></details>
                <details><summary>Reproducible evaluation <span>Maven · JUnit · fresh JVMs</span></summary><p>The project includes 90 passing tests, benchmark runners, recorded predictions, and source documentation. The separate BETH host-log evaluation reproduced 99.40% recall and a 5.59% false-positive rate on its bundled sample. Those numbers describe host logs. The Ant and Maven results measure the JVM path audit.</p></details>
              </div>
            </section>
            """;
}
