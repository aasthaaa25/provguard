package com.provguard.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Read-only tail of the agent's JSONL log.
 *
 * <p>The file is only ever opened for reading. The index keeps a compact summary per event (bounded by
 * {@code maxIndexed}) plus the byte range of the original line, so detail views read the file on demand and
 * large logs are never loaded into memory whole. A trailing partial line (the agent is mid-write) is left for the
 * next poll. A smaller file, a different file, or a changed file head is treated as rotation and the index is
 * rebuilt from the start.
 */
public final class EventLogIndex implements AutoCloseable {
    public interface Listener {
        void onEvent(EventSummary summary);

        void onReset(long epoch);
    }

    public record Malformed(long line, String reason) {
    }

    public record Status(
            String state,
            boolean exists,
            long sizeBytes,
            long lastModifiedMs,
            long bytesRead,
            long linesRead,
            long malformedLines,
            long duplicateEvents,
            int indexedEvents,
            long evictedEvents,
            long acceptedEvents,
            long resets,
            long lastPollMs,
            String lastError,
            long epoch,
            long latestSeq,
            int maxIndexed,
            List<Malformed> recentMalformed) {
    }

    public record Page(List<EventSummary> items, int totalMatching, Long nextBefore, long latestSeq, long epoch,
                       boolean truncated) {
    }

    public static final class SourceChanged extends Exception {
        private static final long serialVersionUID = 1L;

        public SourceChanged(String message) {
            super(message);
        }
    }

    private static final int MAX_LINE = 1 << 20;
    private static final int HEAD_BYTES = 128;

    private final Path path;
    private final int maxIndexed;
    private final long pollMillis;
    private final ObjectMapper mapper = new ObjectMapper();
    private final EventParser parser = new EventParser(mapper);
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService scheduler;

    private final List<EventSummary> items = new ArrayList<>();
    private final Map<String, EventSummary> byId = new HashMap<>();
    private final List<Malformed> malformedRecent = new ArrayList<>();
    private long position;
    private long lineNumber;
    private long malformed;
    private long duplicates;
    private long evicted;
    private long accepted;
    private long resets;
    private long nextSeq;
    private long epoch = System.currentTimeMillis();
    private long lastPollMs;
    private long lastSize;
    private long lastModified;
    private boolean exists;
    private boolean everSeen;
    private String lastError;
    private Object fileKey;
    private byte[] head = new byte[0];

    public EventLogIndex(Path path, int maxIndexed, long pollMillis) {
        this.path = path;
        this.maxIndexed = maxIndexed;
        this.pollMillis = pollMillis;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    /** Indexes what is already in the file, then keeps polling on a daemon thread. */
    public void start() {
        safePoll();
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "provguard-web-log-poller");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::safePoll, pollMillis, pollMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void safePoll() {
        try {
            poll();
        } catch (Throwable failure) {
            synchronized (this) {
                lastError = failure.getClass().getSimpleName();
            }
        }
    }

    /** One incremental read. Public so tests can drive it without timers. */
    public void poll() {
        List<EventSummary> added = new ArrayList<>();
        boolean reset = false;
        long epochAfter;
        synchronized (this) {
            lastPollMs = System.currentTimeMillis();
            try {
                if (!Files.isRegularFile(path)) {
                    exists = false;
                    lastSize = 0;
                    if (everSeen && (position > 0 || !items.isEmpty())) {
                        resetLocked();
                        reset = true;
                    }
                } else {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    exists = true;
                    everSeen = true;
                    long size = attrs.size();
                    Object key = attrs.fileKey();
                    boolean replaced = key != null && fileKey != null && !key.equals(fileKey);
                    if (position > 0 && (size < position || replaced || headChanged())) {
                        resetLocked();
                        reset = true;
                    }
                    fileKey = key;
                    lastSize = size;
                    lastModified = attrs.lastModifiedTime().toMillis();
                    if (size > position) {
                        readNew(added);
                    }
                    lastError = null;
                }
            } catch (IOException exception) {
                lastError = exception.getClass().getSimpleName();
            }
            epochAfter = epoch;
        }
        if (reset) {
            for (Listener listener : listeners) {
                safe(() -> listener.onReset(epochAfter));
            }
        }
        for (EventSummary summary : added) {
            for (Listener listener : listeners) {
                safe(() -> listener.onEvent(summary));
            }
        }
    }

    private static void safe(Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException ignored) {
            // A failing listener must not break indexing.
        }
    }

    private boolean headChanged() throws IOException {
        if (head.length == 0) {
            return false;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(head.length);
            int read = 0;
            while (buffer.hasRemaining()) {
                int n = channel.read(buffer, read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return read < head.length || !Arrays.equals(buffer.array(), head);
        }
    }

    private void resetLocked() {
        items.clear();
        byId.clear();
        malformedRecent.clear();
        position = 0;
        lineNumber = 0;
        malformed = 0;
        duplicates = 0;
        evicted = 0;
        accepted = 0;
        head = new byte[0];
        fileKey = null;
        resets++;
        epoch++;
    }

    private void readNew(List<EventSummary> added) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.position(position);
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            byte[] line = new byte[4096];
            int length = 0;
            boolean oversize = false;
            long lineStart = position;
            long cursor = position;
            while (true) {
                buffer.clear();
                int read = channel.read(buffer);
                if (read < 0) {
                    break;
                }
                buffer.flip();
                while (buffer.hasRemaining()) {
                    byte b = buffer.get();
                    cursor++;
                    if (b == '\n') {
                        handleLine(line, length, oversize, lineStart, added);
                        lineStart = cursor;
                        length = 0;
                        oversize = false;
                    } else if (!oversize) {
                        if (length >= MAX_LINE) {
                            oversize = true;
                        } else {
                            if (length == line.length) {
                                line = Arrays.copyOf(line, Math.min(MAX_LINE, line.length * 2));
                            }
                            line[length++] = b;
                        }
                    }
                }
            }
            // Anything after the last newline is a partial line. Leave it for the next poll.
            if (head.length == 0 && lineStart > 0) {
                captureHead(channel, (int) Math.min(HEAD_BYTES, lineStart));
            }
            position = lineStart;
        }
    }

    /** Fingerprint of the first bytes of the file, taken only from complete lines, to detect replacement. */
    private void captureHead(FileChannel channel, int want) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(want);
        int read = 0;
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, read);
            if (n < 0) {
                break;
            }
            read += n;
        }
        head = read == want ? buffer.array() : Arrays.copyOf(buffer.array(), read);
    }

    private void handleLine(byte[] line, int length, boolean oversize, long offset, List<EventSummary> added) {
        lineNumber++;
        int end = length;
        if (end > 0 && line[end - 1] == '\r') {
            end--;
        }
        if (oversize) {
            recordMalformed("Line is larger than 1 MiB");
            return;
        }
        boolean blank = true;
        for (int i = 0; i < end; i++) {
            if (line[i] != ' ' && line[i] != '\t') {
                blank = false;
                break;
            }
        }
        if (blank) {
            return;
        }
        try {
            JsonNode root = parser.readTree(line, end);
            String id = EventParser.idFor(root, line, end);
            if (byId.containsKey(id)) {
                duplicates++;
                return;
            }
            EventSummary summary = parser.summarize(root, line, end, ++nextSeq, offset);
            items.add(summary);
            byId.put(summary.id(), summary);
            accepted++;
            added.add(summary);
            if (items.size() > maxIndexed + Math.max(16, maxIndexed / 10)) {
                int drop = items.size() - maxIndexed;
                for (int i = 0; i < drop; i++) {
                    byId.remove(items.get(i).id());
                }
                items.subList(0, drop).clear();
                evicted += drop;
            }
        } catch (EventParser.MalformedRecord record) {
            recordMalformed(record.getMessage());
        }
    }

    private void recordMalformed(String reason) {
        malformed++;
        malformedRecent.add(new Malformed(lineNumber, reason));
        if (malformedRecent.size() > 10) {
            malformedRecent.remove(0);
        }
    }

    public synchronized Status status() {
        String state = !exists ? "missing" : lastError != null ? "error" : "ok";
        return new Status(
                state, exists, lastSize, lastModified, position, lineNumber, malformed, duplicates, items.size(),
                evicted, accepted, resets, lastPollMs, lastError, epoch, nextSeq, maxIndexed,
                List.copyOf(malformedRecent));
    }

    public synchronized long epoch() {
        return epoch;
    }

    public synchronized long latestSeq() {
        return nextSeq;
    }

    /** A copy of the indexed summaries, oldest first. */
    public synchronized List<EventSummary> snapshot() {
        return new ArrayList<>(items);
    }

    public synchronized EventSummary find(String id) {
        return byId.get(id);
    }

    public synchronized EventSummary latest() {
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    /** Newest first. {@code before} and {@code after} are exclusive sequence numbers. */
    public synchronized Page query(EventFilter filter, int limit, Long before, Long after) {
        List<EventSummary> out = new ArrayList<>();
        int total = 0;
        boolean truncated = false;
        Long nextBefore = null;
        for (int i = items.size() - 1; i >= 0; i--) {
            EventSummary e = items.get(i);
            if (!filter.matches(e)) {
                continue;
            }
            total++;
            if (before != null && e.seq() >= before) {
                continue;
            }
            if (after != null && e.seq() <= after) {
                continue;
            }
            if (out.size() < limit) {
                out.add(e);
            } else {
                truncated = true;
            }
        }
        if (truncated && !out.isEmpty()) {
            nextBefore = out.get(out.size() - 1).seq();
        }
        return new Page(out, total, nextBefore, nextSeq, epoch, truncated);
    }

    /** Reads the original line for one event and verifies it still is that event. */
    public JsonNode readRecord(EventSummary summary) throws IOException, SourceChanged {
        byte[] data = new byte[summary.length()];
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (summary.offset() + summary.length() > file.length()) {
                throw new SourceChanged("The log is shorter than when this event was indexed");
            }
            file.seek(summary.offset());
            file.readFully(data);
        } catch (java.io.FileNotFoundException exception) {
            throw new SourceChanged("The log file is not available");
        }
        try {
            JsonNode root = parser.readTree(data, data.length);
            if (!summary.id().equals(EventParser.idFor(root, data, data.length))) {
                throw new SourceChanged("The log changed since this event was indexed");
            }
            return root;
        } catch (EventParser.MalformedRecord record) {
            throw new SourceChanged("The log changed since this event was indexed");
        }
    }

    public EventParser parser() {
        return parser;
    }

    public Path path() {
        return path;
    }
}
