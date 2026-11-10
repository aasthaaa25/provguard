package com.provguard.web;

import java.io.IOException;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Starts the read-only dashboard bridge. It does not start, stop, or configure the agent. */
public final class WebMain {
    private WebMain() {
    }

    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                System.out.print(WebConfig.usage());
                return;
            }
        }
        WebConfig config;
        try {
            config = WebConfig.parse(args);
        } catch (IllegalArgumentException invalid) {
            System.err.println("ProvGuard web: " + invalid.getMessage());
            System.err.println();
            System.err.print(WebConfig.usage());
            System.exit(2);
            return;
        }

        Path eventsFile = config.eventsFile();
        ScheduledExecutorService demoWriter = null;
        if (config.demo()) {
            eventsFile = Files.createTempFile("provguard-demo-", ".jsonl");
            eventsFile.toFile().deleteOnExit();
            List<String> lines = DemoData.generate(System.currentTimeMillis());
            Files.write(eventsFile, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
            demoWriter = startDemoWriter(eventsFile);
        }

        EventLogIndex index = new EventLogIndex(eventsFile, config.maxIndexed(), config.pollMillis());
        SseHub hub = new SseHub();
        index.addListener(hub);
        index.start();

        String version = version();
        ApiServer server = new ApiServer(config, index, hub, Instant.now(), version);
        try {
            server.start();
        } catch (BindException busy) {
            System.err.println("ProvGuard web: " + config.host() + ":" + config.port()
                    + " is not available (" + busy.getMessage() + "). Try --port <other>.");
            System.exit(1);
            return;
        }

        String url = "http://" + (config.host().contains(":") ? "[" + config.host() + "]" : config.host()) + ":" + server.port() + "/";
        System.out.println("ProvGuard web dashboard " + version);
        System.out.println("  Dashboard : " + url);
        if (config.demo()) {
            System.out.println("  Source    : DEMO DATA, synthetic events. Not live telemetry.");
        } else {
            System.out.println("  Source    : " + eventsFile.toAbsolutePath() + (Files.isRegularFile(eventsFile) ? "" : " (not found yet)"));
        }
        System.out.println("  Access    : read-only" + (config.allowRemote() ? ", remote access enabled" : ", local only"));
        System.out.println("  The dashboard never changes the agent. Press Ctrl+C to stop.");

        ScheduledExecutorService writer = demoWriter;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            index.close();
            if (writer != null) {
                writer.shutdownNow();
            }
        }, "provguard-web-shutdown"));
        Thread.currentThread().join();
    }

    private static ScheduledExecutorService startDemoWriter(Path file) {
        Random random = new Random();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "provguard-web-demo-writer");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                String line = DemoData.event(random, System.currentTimeMillis(), com.provguard.core.AgentMode.ALERT) + "\n";
                Files.write(file, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Demo only. Nothing to recover.
            }
        }, 4, 4, TimeUnit.SECONDS);
        return scheduler;
    }

    static String version() {
        String implementation = WebMain.class.getPackage().getImplementationVersion();
        return implementation == null ? "1.0.0-dev" : implementation;
    }
}
