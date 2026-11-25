package com.provguard.agent.study;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.provguard.rules.BlockingPolicy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Reproducible external-tool evaluation using stock Ant and Maven entry points. */
public final class ExternalApplicationStudy {
    private static final int PROBES = 4;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PROBE = "com.provguard.demo.perf.ExternalStudyProbe";
    private static final List<String> SCENARIOS = List.of("nominal", "input-change", "workflow-change", "policy-deviation");

    private record Tool(String name, List<String> launcher, String packages, Path base) {}
    private record Run(List<JsonNode> events, double millis) {}
    private record Observation(String id, long thread, OrderedPathProfile.MethodPath path, boolean ruleAlert) {}
    private record Counts(int events, int rules, int stacks, int transitions, int flaggedRuns) {
        Counts plus(Counts other) {
            return new Counts(events + other.events, rules + other.rules, stacks + other.stacks,
                    transitions + other.transitions, flaggedRuns + other.flaggedRuns);
        }
    }

    private ExternalApplicationStudy() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("Usage: ExternalApplicationStudy <agent.jar> <demo.jar> "
                    + "<ant-home> <maven-home> <new-output-directory>");
        }
        Path agent = absolute(args[0]);
        Path demo = absolute(args[1]);
        Path ant = absolute(args[2]);
        Path maven = absolute(args[3]);
        Path output = Files.createDirectory(absolute(args[4]));
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        Path antBase = Files.createDirectory(output.resolve("ant"));
        Path mavenBase = Files.createDirectory(output.resolve("maven"));
        var tools = List.of(
                new Tool("Apache Ant", List.of("-cp", ant.resolve("lib") + File.separator + "*",
                        "org.apache.tools.ant.Main"),
                        "org.apache.tools.ant", antBase),
                new Tool("Apache Maven", mavenLauncher(maven, mavenBase),
                        "org.apache.maven,org.codehaus.mojo,org.codehaus.plexus,org.apache.tools.ant", mavenBase));
        createInputs(antBase, mavenBase, java, demo);

        StringBuilder result = new StringBuilder();
        StringBuilder overhead = new StringBuilder();
        StringBuilder samples = new StringBuilder("application,repetition,plain_ms,monitor_ms,monitor_events\n");
        StringBuilder predictions = new StringBuilder("application,scenario,run,event_id,rules,ordered_snapshot,virtual_transition\n");
        for (Tool tool : tools) {
            // Resolve third-party plugins before timing. The actual Maven experiment runs offline.
            if (tool.name().equals("Apache Maven")) {
                launch(java, agent, tool, "nominal", "prime-exec", false, false);
                launch(java, agent, tool, "policy-deviation", "prime-antrun", false, false);
            }
            var training = new ArrayList<List<Observation>>();
            for (int run = 1; run <= 2; run++) {
                training.add(observations(launch(java, agent, tool, "nominal", "train-" + run, true, true)));
            }
            OrderedPathProfile profile = train(training);
            OrderedPathProfile.write(tool.base().resolve("profile.json"), profile);
            for (String scenario : SCENARIOS) {
                Counts total = new Counts(0, 0, 0, 0, 0);
                for (int run = 1; run <= 2; run++) {
                    var rows = observations(launch(java, agent, tool, scenario, scenario + "-" + run, true, true));
                    total = total.plus(score(tool, profile, rows, scenario, run, predictions));
                }
                result.append("| %s | %s | %d | %d | %d | %d | %d/2 |\n".formatted(
                        tool.name(), scenario, total.events(), total.rules(), total.stacks(), total.transitions(), total.flaggedRuns()));
            }

            var plain = new double[3];
            var monitor = new double[3];
            for (int i = 0; i < plain.length; i++) {
                String label = "timing-" + (i + 1);
                Run baseline;
                Run monitored;
                if (i % 2 == 0) {
                    baseline = launch(java, agent, tool, "nominal", label + "-plain", false, true);
                    monitored = launch(java, agent, tool, "nominal", label + "-monitor", true, true);
                } else {
                    monitored = launch(java, agent, tool, "nominal", label + "-monitor", true, true);
                    baseline = launch(java, agent, tool, "nominal", label + "-plain", false, true);
                }
                plain[i] = baseline.millis();
                monitor[i] = monitored.millis();
                samples.append(String.format(Locale.ROOT, "%s,%d,%.3f,%.3f,%d\n", tool.name(), i + 1,
                        plain[i], monitor[i], monitored.events().size()));
            }
            Arrays.sort(plain);
            Arrays.sort(monitor);
            overhead.append(String.format(Locale.ROOT, "| %s | %.1f | %.1f | %+.1f | %+.1f%% |\n",
                    tool.name(), plain[1], monitor[1], monitor[1] - plain[1], 100 * (monitor[1] / plain[1] - 1)));
        }

        String report = """
                # External Java application study

                Parent JDK %s on %s (%s). Applications are the stock Apache Ant and Apache Maven CLI entry points.
                Each application trains on two fresh nominal JVMs and tests on two separate JVMs per scenario.
                The Java harness generates only build configuration. All runs invoke four local, harmless Java
                readiness probes. No scenario exercises an exploit. The policy-deviation scenario deliberately uses
                an alternate launcher that a hypothetical build policy reserves; the launched operation stays the same.

                Nominal and input-change runs use the same task route. Input changes the probe argument.
                A benign workflow change wraps the Ant Exec task in Sequential or invokes Maven's same plugin goal directly.
                Policy deviation uses Ant's forked Java task instead of Exec, or Maven Antrun instead of Exec Maven Plugin.
                Qualified names are fit per application on benign logs only. The virtual audit flags unseen transitions,
                including the first observed path after a thread starts. Counts refer to sink events, which are correlated.
                A run is flagged if it contains at least one virtual transition alert. Rules use the standard alert threshold.

                | Application | Scenario | Events | Rules alerts | Ordered snapshot alerts | Virtual transition alerts | Flagged runs |
                | --- | --- | ---: | ---: | ---: | ---: | ---: |
                %s
                ## End-to-end monitoring cost

                Three alternating-order, fresh-JVM pairs per application, with plugin resolution completed first.
                Values are medians of full child-process wall time in milliseconds, including JVM startup, build tool,
                four forked probes, agent startup where applicable, rule/model work, and log flushing.
                The audit is offline and excluded from wall-time measurements. These are single-host, short-run timings.

                | Application | No agent (ms) | Monitor (ms) | Delta (ms) | Delta |
                | --- | ---: | ---: | ---: | ---: |
                %s
                Raw logs, exact generated build inputs, profiles, predictions.csv and timing.csv accompany the report.
                Results concern external applications on controlled build jobs, with one monitored API family and
                a simulated policy deviation. They do not estimate production attack-detection accuracy.
                """.formatted(Runtime.version(), System.getProperty("os.name"), System.getProperty("os.arch"), result, overhead);
        Files.writeString(output.resolve("external-results.md"), report);
        Files.writeString(output.resolve("timing.csv"), samples);
        Files.writeString(output.resolve("predictions.csv"), predictions);
        System.out.print(report);
    }

    private static Path absolute(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static List<String> mavenLauncher(Path home, Path base) throws IOException {
        Path bootstrap;
        try (Stream<Path> files = Files.list(home.resolve("boot"))) {
            bootstrap = files.filter(path -> path.getFileName().toString().matches("plexus-classworlds-.*\\.jar"))
                    .findFirst().orElseThrow(() -> new IOException("Missing Maven classworlds under " + home));
        }
        return List.of("-Dmaven.home=" + home, "-Dclassworlds.conf=" + home.resolve("bin/m2.conf"),
                "-Dmaven.multiModuleProjectDirectory=" + base, "-cp", bootstrap.toString(),
                "org.codehaus.plexus.classworlds.launcher.Launcher", "-B", "-Dstyle.color=never");
    }

    private static Run launch(String java, Path agent, Tool tool, String scenario, String label, boolean monitor, boolean offline)
            throws IOException, InterruptedException {
        Path events = tool.base().resolve(label + ".jsonl");
        Path console = tool.base().resolve(label + ".log");
        List<String> command = new ArrayList<>();
        command.add(java);
        for (String key : List.of("javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword")) {
            String value = System.getProperty(key);
            if (value != null) {
                command.add("-D" + key + "=" + value);
            }
        }
        if (monitor) {
            command.add("-javaagent:" + agent + "=mode=MONITOR;verbose=false;queueCapacity=2048;frequencyThreshold=1000"
                    + ";appPackages=" + tool.packages() + ";knownPackages=" + tool.packages() + ";events=" + events);
        }
        command.addAll(tool.launcher());
        if (tool.name().equals("Apache Ant")) {
            command.addAll(List.of("-f", scenario + ".xml"));
        } else {
            if (offline) {
                command.add("-o");
            }
            command.addAll(List.of("-f", scenario + ".xml"));
            if (scenario.equals("workflow-change")) {
                for (int i = 1; i <= PROBES; i++) {
                    command.add("org.codehaus.mojo:exec-maven-plugin:3.5.0:exec@probe-" + i);
                }
            } else {
                command.add("validate");
            }
        }
        long start = System.nanoTime();
        Process child = new ProcessBuilder(command).directory(tool.base().toFile())
                .redirectErrorStream(true).redirectOutput(console.toFile()).start();
        try {
            if (!child.waitFor(120, TimeUnit.SECONDS) || child.exitValue() != 0) {
                throw new IOException("Failed " + tool.name() + " " + label + ". See " + console);
            }
        } finally {
            if (child.isAlive()) {
                child.descendants().forEach(ProcessHandle::destroyForcibly);
                child.destroyForcibly();
            }
        }
        double duration = (System.nanoTime() - start) / 1_000_000.0;
        String stdout = Files.readString(console);
        long probes = stdout.lines().filter(line -> line.contains("PROVGUARD_EXTERNAL_READY")).count();
        if (probes != PROBES) {
            throw new IOException("Expected " + PROBES + " successful probes in " + console + " but got " + probes);
        }
        if (!monitor) {
            return new Run(List.of(), duration);
        }
        List<JsonNode> rows = new ArrayList<>();
        for (String line : Files.readAllLines(events)) {
            rows.add(JSON.readTree(line));
        }
        if (rows.size() < PROBES || !stdout.contains("dropped=0")) {
            throw new IOException("Missing or dropped telemetry in " + console);
        }
        return new Run(rows, duration);
    }

    private static List<Observation> observations(Run run) throws IOException {
        List<Observation> result = new ArrayList<>();
        for (JsonNode row : run.events()) {
            var path = OrderedPathProfile.path(row, true);
            if (path.methods().isEmpty() || row.required("enforced").asBoolean()) {
                throw new IOException("Expected a monitored application stack and no enforcement");
            }
            result.add(new Observation(row.required("eventId").asText(), row.required("threadId").asLong(), path,
                    row.required("ruleScore").asInt() >= BlockingPolicy.MEDIUM_RULE_SCORE));
        }
        return result;
    }

    private static OrderedPathProfile train(List<List<Observation>> logs) {
        Set<OrderedPathProfile.MethodPath> paths = new HashSet<>();
        Set<OrderedPathProfile.VirtualPath> transitions = new HashSet<>();
        for (var log : logs) {
            var previous = new HashMap<Long, OrderedPathProfile.MethodPath>();
            for (var row : log) {
                paths.add(row.path());
                transitions.add(OrderedPathProfile.VirtualPath.between(previous.put(row.thread(), row.path()), row.path()));
            }
        }
        return new OrderedPathProfile(true, paths, transitions);
    }

    private static Counts score(Tool tool, OrderedPathProfile profile, List<Observation> rows, String scenario,
                                int run, StringBuilder output) {
        int rules = 0;
        int stacks = 0;
        int transitions = 0;
        var previous = new HashMap<Long, OrderedPathProfile.MethodPath>();
        for (var row : rows) {
            boolean stack = !profile.known().contains(row.path());
            var transition = OrderedPathProfile.VirtualPath.between(previous.put(row.thread(), row.path()), row.path());
            boolean novel = !profile.transitions().contains(transition);
            rules += row.ruleAlert() ? 1 : 0;
            stacks += stack ? 1 : 0;
            transitions += novel ? 1 : 0;
            output.append("%s,%s,%d,%s,%s,%s,%s\n".formatted(tool.name(), scenario, run, row.id(),
                    row.ruleAlert(), stack, novel));
        }
        return new Counts(rows.size(), rules, stacks, transitions, transitions == 0 ? 0 : 1);
    }

    private static void createInputs(Path ant, Path maven, String java, Path demo) throws IOException {
        for (String scenario : SCENARIOS) {
            String probeArg = scenario.equals("input-change") ? "alternate" : "default";
            String exec = "<exec executable=\"%s\" failonerror=\"true\">%s</exec>\n"
                    .formatted(xml(java), arguments(demo, probeArg, "arg"));
            String fork = "<java classname=\"%s\" fork=\"true\" failonerror=\"true\" jvm=\"%s\" classpath=\"%s\">"
                    .formatted(PROBE, xml(java), xml(demo.toString())) + "<arg value=\"" + probeArg + "\"/></java>\n";
            String tasks = switch (scenario) {
                case "workflow-change" -> ("<sequential>" + exec + "</sequential>\n").repeat(PROBES);
                case "policy-deviation" -> fork.repeat(PROBES);
                default -> exec.repeat(PROBES);
            };
            Files.writeString(ant.resolve(scenario + ".xml"), "<project default=\"run\"><target name=\"run\">\n"
                    + tasks + "</target></project>\n");

            String plugin;
            if (scenario.equals("policy-deviation")) {
                plugin = """
                        <plugin><artifactId>maven-antrun-plugin</artifactId><version>3.1.0</version>
                        <executions><execution><phase>validate</phase><goals><goal>run</goal></goals>
                        <configuration><target>%s</target></configuration></execution></executions></plugin>
                        """.formatted(exec.repeat(PROBES));
            } else {
                var executions = new StringBuilder();
                for (int i = 1; i <= PROBES; i++) {
                    executions.append("<execution><id>probe-").append(i)
                            .append("</id><phase>validate</phase><goals><goal>exec</goal></goals></execution>");
                }
                plugin = """
                        <plugin><groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId>
                        <version>3.5.0</version><configuration><executable>%s</executable><arguments>%s</arguments></configuration>
                        <executions>%s</executions></plugin>
                        """.formatted(xml(java), arguments(demo, probeArg, "argument"), executions);
            }
            Files.writeString(maven.resolve(scenario + ".xml"), """
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion><groupId>com.provguard.study</groupId>
                    <artifactId>external-probe</artifactId><version>1.0</version><packaging>pom</packaging>
                    <build><plugins>%s</plugins></build></project>
                    """.formatted(plugin));
        }
    }

    private static String arguments(Path demo, String value, String element) {
        List<String> values = List.of("-cp", demo.toString(), PROBE, value);
        var xml = new StringBuilder();
        for (String arg : values) {
            if (element.equals("arg")) {
                xml.append("<arg value=\"").append(xml(arg)).append("\"/>");
            } else {
                xml.append("<argument>").append(xml(arg)).append("</argument>");
            }
        }
        return xml.toString();
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }
}
