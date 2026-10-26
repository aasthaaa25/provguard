package com.provguard.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;

/**
 * Startup configuration for the dashboard bridge.
 * The default bind address is loopback. Any other address needs {@code --allow-remote}.
 */
public record WebConfig(
        Path eventsFile,
        Path reportsDir,
        String host,
        int port,
        boolean allowRemote,
        boolean demo,
        int maxIndexed,
        long pollMillis,
        long activeWindowSeconds) {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8765;

    public static WebConfig defaults() {
        return parse(new String[0]);
    }

    public static String usage() {
        return """
                ProvGuard web dashboard (read-only bridge over the agent's JSONL log)

                Options:
                  --events <file>        JSONL log written by the agent (default: events.jsonl)
                  --reports <dir>        Directory holding ml-evaluation.md (default: reports)
                  --host <address>       Bind address (default: 127.0.0.1)
                  --port <port>          Bind port (default: 8765, 0 picks a free port)
                  --allow-remote         Required to bind a non-loopback address. Not recommended.
                  --demo                 Serve clearly labelled synthetic events instead of the real log
                  --max-events <n>       Most recent events kept in the in-memory index (default: 50000)
                  --poll-ms <n>          Log polling interval in milliseconds (default: 1000)
                  --active-window <sec>  Seconds of log silence before the agent shows as idle (default: 300)
                  --help                 Show this help
                """;
    }

    public static WebConfig parse(String[] args) {
        Path events = Path.of("events.jsonl");
        Path reports = Path.of("reports");
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        boolean remote = false;
        boolean demo = false;
        int maxIndexed = 50_000;
        long poll = 1_000;
        long window = 300;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--events" -> events = Path.of(value(args, ++i, arg));
                case "--reports" -> reports = Path.of(value(args, ++i, arg));
                case "--host" -> host = value(args, ++i, arg);
                case "--port" -> port = integer(value(args, ++i, arg), arg, 0, 65_535);
                case "--allow-remote" -> remote = true;
                case "--demo" -> demo = true;
                case "--max-events" -> maxIndexed = integer(value(args, ++i, arg), arg, 100, 5_000_000);
                case "--poll-ms" -> poll = integer(value(args, ++i, arg), arg, 100, 60_000);
                case "--active-window" -> window = integer(value(args, ++i, arg), arg, 5, 86_400);
                default -> throw new IllegalArgumentException("Unknown option " + arg);
            }
        }
        if (!remote && !isLoopback(host)) {
            throw new IllegalArgumentException(
                    "Refusing to bind " + host + ". The dashboard is local-only unless --allow-remote is given.");
        }
        return new WebConfig(events, reports, host, port, remote, demo, maxIndexed, poll, window);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static int integer(String raw, String option, int min, int max) {
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(option + " must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(option + " is not an integer: " + raw);
        }
    }

    static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
