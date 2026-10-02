package com.provguard.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.provguard.core.AgentMode;
import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;
import com.provguard.core.exception.EventLoggingException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

public final class JsonlEventWriter implements EventWriter, SecurityEventObserver {
    private final Path path;
    private final AgentMode mode;
    private final ObjectMapper mapper;
    private final JsonEventMapper events = new JsonEventMapper();
    private final Object lock = new Object();
    private final AtomicLong lastErrorAt = new AtomicLong();
    private BufferedWriter writer;

    public JsonlEventWriter(Path path, AgentMode mode) {
        this.path = path;
        this.mode = mode;
        this.mapper = new ObjectMapper();
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            this.writer = null;
            report(exception);
        }
    }

    @Override
    public void write(SecurityEvent event, RiskResult result) {
        if (writer == null) {
            return;
        }
        try {
            String line = mapper.writeValueAsString(events.toMap(event, result, mode));
            synchronized (lock) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException exception) {
            report(exception);
        }
    }

    @Override
    public void onEvent(SecurityEvent event, RiskResult result) {
        write(event, result);
    }

    @Override
    public void close() {
        if (writer == null) {
            return;
        }
        try {
            synchronized (lock) {
                writer.close();
            }
        } catch (IOException exception) {
            report(exception);
        }
    }

    private void report(IOException exception) {
        long now = System.currentTimeMillis();
        long previous = lastErrorAt.get();
        if (now - previous < 5_000 || !lastErrorAt.compareAndSet(previous, now)) {
            return;
        }
        System.err.println("ProvGuard JSONL logging failed. Target execution continues. " + exception.getMessage());
    }

    public Path path() {
        return path;
    }
}
