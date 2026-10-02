package com.provguard.it;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAttachmentIT {

    @Test
    void safeDemoIsMonitoredAndDoesNotBlock() throws Exception {
        Run run = launch("MONITOR", "com.provguard.demo.safe.SafeDemoApplication");
        assertEquals(0, run.exitCode, run.output);
        assertTrue(run.output.contains("ProvGuard Agent Started Successfully"), run.output);
        assertTrue(run.events.contains("PROCESS_BUILDER_START"), run.events);
        assertTrue(run.events.contains("RUNTIME_EXEC"), run.events);
        assertTrue(run.events.contains("DESERIALIZATION_RESOLVE_CLASS"), run.events);
        assertTrue(run.events.contains("\"modelStatus\":\"AVAILABLE\""), run.events);
        assertFalse(run.events.contains("provguard-ok"), run.events);
        assertTrue(run.events.contains("sha256:"), run.events);
    }

    @Test
    void alertModeDoesNotBlockTheSuspiciousDemo() throws Exception {
        Run run = launch("ALERT", "com.provguard.demo.suspicious.SuspiciousPathDemo");
        assertEquals(0, run.exitCode, run.output);
        assertTrue(run.output.contains("Suspicious alert path finished"), run.output);
        assertTrue(run.output.contains("logged without blocking"), run.output);
        assertFalse(run.output.contains("was blocked"), run.output);
        assertTrue(run.events.contains("DANGEROUS_COMMAND_KEYWORD") || run.events.contains("\"decision\":\"ALERT\""), run.events);
    }

    @Test
    void blockModeDeniesTheDenylistClassOnly() throws Exception {
        Run run = launch("BLOCK", "com.provguard.demo.safe.DeserializationDemo", "blocked");
        assertEquals(0, run.exitCode, run.output);
        assertTrue(run.output.contains("was denied"), run.output);
        assertTrue(run.events.contains("\"enforced\":true"), run.events);
        assertTrue(run.events.contains("UNSAFE_DESERIALIZATION_CLASS"), run.events);
        assertTrue(run.events.contains("BlockedDemoObject"), run.events);
    }

    private static Run launch(String mode, String main, String... args) throws Exception {
        Path agent = Path.of(System.getProperty("provguard.it.agentJar"));
        Path demo = Path.of(System.getProperty("provguard.it.demoJar"));
        assertTrue(Files.isRegularFile(agent), "Build the agent first: " + agent);
        assertTrue(Files.isRegularFile(demo), "Build the demo first: " + demo);
        Path dir = Files.createTempDirectory("provguard-it");
        Path events = dir.resolve("events.jsonl");
        Path stdout = dir.resolve("stdout.txt");
        Path stderr = dir.resolve("stderr.txt");
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>();
        command.add(java);
        command.add("-javaagent:" + agent.toAbsolutePath() + "=mode=" + mode
                + ";verbose=false;events=" + events.toAbsolutePath());
        command.add("-cp");
        command.add(demo.toAbsolutePath().toString());
        command.add(main);
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "demo timed out");
        String output = Files.readString(stdout) + System.lineSeparator() + Files.readString(stderr);
        String body = Files.exists(events) ? Files.readString(events) : "";
        return new Run(process.exitValue(), output, body);
    }

    private record Run(int exitCode, String output, String events) {
    }
}
