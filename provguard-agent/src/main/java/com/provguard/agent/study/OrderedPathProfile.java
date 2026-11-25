package com.provguard.agent.study;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** A per-sink baseline of application method sequences observed during benign execution. */
public record OrderedPathProfile(boolean qualified, Set<MethodPath> known, Set<VirtualPath> transitions) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public OrderedPathProfile {
        known = Set.copyOf(known);
        transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
        if (known.isEmpty() || known.stream().anyMatch(path -> path.methods().isEmpty())) {
            throw new IllegalArgumentException("A baseline needs nonempty application paths");
        }
    }

    OrderedPathProfile(boolean qualified, Set<MethodPath> known) {
        this(qualified, known, Set.of());
    }

    public record MethodPath(String sink, List<String> methods) {
        public MethodPath {
            methods = List.copyOf(methods);
        }

        MethodPath unordered() {
            return new MethodPath(sink, methods.stream().sorted().toList());
        }
    }

    /** The entry/exit portion between consecutive observed sinks, using stack methods as tokens. */
    public record VirtualPath(String previousSink, String currentSink, int sharedDepth,
                              List<String> exits, List<String> entries) {
        public VirtualPath {
            exits = List.copyOf(exits);
            entries = List.copyOf(entries);
        }

        static VirtualPath between(MethodPath previous, MethodPath current) {
            if (previous == null) {
                return new VirtualPath("START", current.sink(), 0, List.of(), current.methods());
            }
            int shared = 0;
            int limit = Math.min(previous.methods().size(), current.methods().size());
            while (shared < limit && previous.methods().get(shared).equals(current.methods().get(shared))) {
                shared++;
            }
            return new VirtualPath(previous.sink(), current.sink(), shared,
                    previous.methods().subList(shared, previous.methods().size()).reversed(),
                    current.methods().subList(shared, current.methods().size()));
        }
    }

    public String classify(MethodPath path) {
        if (path.methods().isEmpty()) {
            return "NO_APPLICATION_PATH";
        }
        return known.contains(path) ? "KNOWN" : "UNSEEN";
    }

    static MethodPath path(JsonNode event, boolean qualified) {
        JsonNode frames = event.required("callFrames");
        List<String> methods = new ArrayList<>();
        // The agent stores innermost frames first. Profiles read from entry towards the sink.
        for (int index = frames.size() - 1; index >= 0; index--) {
            JsonNode frame = frames.get(index);
            if (frame.path("applicationClass").asBoolean()) {
                String method = frame.required("methodName").asText();
                methods.add(qualified ? frame.required("className").asText() + "#" + method : method);
            }
        }
        return new MethodPath(event.required("sinkType").asText(), methods);
    }

    public static void main(String[] args) throws IOException {
        if (args.length >= 3 && "train".equals(args[0])) {
            Path output = Path.of(args[1]);
            Set<MethodPath> paths = new HashSet<>();
            Set<VirtualPath> transitions = new HashSet<>();
            for (int i = 2; i < args.length; i++) {
                var previous = new HashMap<Long, MethodPath>();
                try (Stream<String> lines = Files.lines(Path.of(args[i]))) {
                    for (String line : (Iterable<String>) lines::iterator) {
                        JsonNode event = JSON.readTree(line);
                        MethodPath path = path(event, true);
                        long thread = event.required("threadId").asLong();
                        if (!path.methods().isEmpty()) {
                            paths.add(path);
                            transitions.add(VirtualPath.between(previous.put(thread, path), path));
                        } else {
                            previous.remove(thread);
                        }
                    }
                }
            }
            write(output, new OrderedPathProfile(true, paths, transitions));
            System.out.println("Learned " + paths.size() + " qualified method paths and "
                    + transitions.size() + " virtual paths");
        } else if (args.length == 3 && "score".equals(args[0])) {
            var profile = JSON.readValue(Path.of(args[1]).toFile(), OrderedPathProfile.class);
            var previous = new HashMap<Long, MethodPath>();
            try (Stream<String> lines = Files.lines(Path.of(args[2]))) {
                for (String line : (Iterable<String>) lines::iterator) {
                    JsonNode event = JSON.readTree(line);
                    MethodPath path = path(event, profile.qualified());
                    long thread = event.required("threadId").asLong();
                    String transition;
                    if (path.methods().isEmpty()) {
                        previous.remove(thread);
                        transition = "NO_APPLICATION_PATH";
                    } else {
                        var virtualPath = VirtualPath.between(previous.put(thread, path), path);
                        transition = profile.transitions().contains(virtualPath) ? "KNOWN_TRANSITION" : "UNSEEN_TRANSITION";
                    }
                    System.out.println(event.required("eventId").asText() + "\t" + profile.classify(path)
                            + "\t" + transition + "\t" + path.sink() + "\t" + String.join(" -> ", path.methods()));
                }
            }
        } else {
            throw new IllegalArgumentException("Usage: OrderedPathProfile train <profile.json> <benign.jsonl>... "
                    + "or OrderedPathProfile score <profile.json> <events.jsonl>");
        }
    }

    static void write(Path output, OrderedPathProfile profile) throws IOException {
        try (var stream = Files.newOutputStream(output, java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(stream, profile);
        }
    }
}
