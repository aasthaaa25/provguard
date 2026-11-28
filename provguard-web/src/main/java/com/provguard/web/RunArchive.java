package com.provguard.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Packages the files produced by one completed, fixed Java experiment. */
final class RunArchive {
    private static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;
    private static final List<String> FILES = List.of("viva-report.json", "profile.json", "events.jsonl",
            "startup.jsonl", "blocked.jsonl", "nominal.jsonl", "sequence.jsonl");

    private RunArchive() {}

    static byte[] build(Path directory) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int remaining = MAX_TOTAL_BYTES;
        try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            write(zip, "README.txt", """
                    ProvGuard controlled Java startup experiment

                    viva-report.json contains the counts and the event identifiers used on the dashboard.
                    profile.json is the ordered-path baseline trained on nominal.jsonl.
                    sequence.jsonl is the separate, reordered test run.
                    startup.jsonl and blocked.jsonl show the agent's Monitor and Block modes.
                    events.jsonl combines all four workloads in that order.

                    Cite the Ant and Maven benchmark separately when discussing external evaluation.
                    """.getBytes(StandardCharsets.UTF_8));
            for (String name : FILES) {
                try (var input = Files.newInputStream(directory.resolve(name))) {
                    byte[] content = input.readNBytes(remaining + 1);
                    if (content.length > remaining) {
                        throw new IOException("The evidence exceeds the archive size limit.");
                    }
                    remaining -= content.length;
                    write(zip, name, content);
                }
            }
        }
        return bytes.toByteArray();
    }

    private static void write(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
