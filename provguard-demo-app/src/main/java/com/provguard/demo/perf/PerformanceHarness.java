package com.provguard.demo.perf;

import com.provguard.demo.LocalCommand;

public final class PerformanceHarness {
    private PerformanceHarness() {
    }

    public static void main(String[] args) throws Exception {
        int iterations = args.length == 0 ? 12 : Integer.parseInt(args[0]);
        long started = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Process process = new ProcessBuilder(LocalCommand.echo("perf-" + i))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.waitFor();
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("iterations=" + iterations);
        System.out.println("elapsedMs=" + elapsedMs);
        System.out.println("avgMs=" + (elapsedMs / (double) iterations));
        System.out.println("usedMemoryBytes=" + used);
    }
}
