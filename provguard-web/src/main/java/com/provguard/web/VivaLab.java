package com.provguard.web;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Runs the fixed, local teaching workloads in a fresh JVM for each rehearsal. */
final class VivaLab implements AutoCloseable {
    private static final int HISTORY_LIMIT = 8;
    private final List<String> command;
    private final Path root;
    private final String token;
    private Path run;
    private Process process;
    private Instant started;
    private Instant finished;
    private boolean failed;
    private final LinkedHashMap<String, Evidence> history = new LinkedHashMap<>();

    record Evidence(String id, Path directory, Instant finishedAt, long elapsedMillis,
                    EventLogIndex index, JsonNode report) {}
    record Status(boolean running, boolean failed, int completed, long elapsedMillis, Evidence evidence) {}

    static VivaLab open() throws IOException {
        Path agent = Path.of("provguard-agent", "target", "provguard-agent-1.0.0.jar").toAbsolutePath();
        Path demo = Path.of("provguard-demo-app", "target", "provguard-demo-app-1.0.0.jar").toAbsolutePath();
        if (!Files.isRegularFile(agent) || !Files.isRegularFile(demo)) {
            throw new IOException("Run mvn verify from the repository root before starting the viva lab.");
        }
        return new VivaLab(agent, demo);
    }

    private VivaLab(Path agent, Path demo) throws IOException {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        command = List.of(java.toString(), "-cp", agent.toString(), "com.provguard.agent.study.VivaRun",
                agent.toString(), demo.toString());
        root = Files.createTempDirectory("provguard-viva-");
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String token() {
        return token;
    }

    synchronized void start() {
        if (process != null) {
            return;
        }
        run = root.resolve(UUID.randomUUID().toString());
        started = Instant.now();
        finished = null;
        failed = false;
        var args = new java.util.ArrayList<>(command);
        args.add(run.toString());
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true)
                    .redirectOutput(run.resolveSibling(run.getFileName() + ".log").toFile()).start();
            Process child = process;
            Path directory = run;
            child.onExit().thenAccept(exited -> complete(exited, directory));
        } catch (IOException error) {
            finished = Instant.now();
            failed = true;
            System.err.println("ProvGuard viva: " + error.getMessage());
        }
    }

    private synchronized void complete(Process child, Path directory) {
        if (process != child) {
            return;
        }
        finished = Instant.now();
        process = null;
        if (child.exitValue() == 0) {
            var index = new EventLogIndex(directory.resolve("events.jsonl"), 200, 1_000);
            index.poll();
            JsonNode report = VivaPage.readReport(directory.resolve("viva-report.json"), index);
            if (report != null) {
                String id = directory.getFileName().toString();
                history.put(id, new Evidence(id, directory, finished, elapsed(finished), index, report));
                if (history.size() > HISTORY_LIMIT) {
                    history.pollFirstEntry();
                }
                return;
            }
        }
        failed = true;
        System.err.println("ProvGuard viva: execution did not yield complete evidence. See "
                + directory.resolveSibling(directory.getFileName() + ".log"));
    }

    synchronized Status status() {
        int completed = run == null ? 0 : (int) List.of("startup", "blocked", "baseline").stream()
                .filter(step -> Files.isRegularFile(run.resolve(step + ".done"))).count();
        if (finished != null && !failed) {
            completed = 4;
        }
        Instant end = finished == null ? Instant.now() : finished;
        var latest = history.lastEntry();
        return new Status(process != null, failed, completed, elapsed(end), latest == null ? null : latest.getValue());
    }

    private long elapsed(Instant end) {
        return started == null ? 0 : Math.max(0, Duration.between(started, end).toMillis());
    }

    synchronized List<Evidence> history() {
        return List.copyOf(history.sequencedValues().reversed());
    }

    synchronized Evidence evidence(String id) {
        return history.get(id);
    }

    @Override
    public synchronized void close() {
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process = null;
        }
    }
}
