package com.provguard.agent.study;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Runs the viva examples with the real agent and prepares evidence for the read-only dashboard. */
public final class VivaRun {
    private static final String FIXTURES = "com.provguard.demo.safe.";

    private VivaRun() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected agent JAR, demo JAR, and a new run directory.");
        }
        Path agent = Path.of(args[0]).toAbsolutePath();
        Path demo = Path.of(args[1]).toAbsolutePath();
        Path run = Files.createDirectory(Path.of(args[2]).toAbsolutePath());
        String cache = FIXTURES + "OrderedWorkloads$Cache";

        var startup = StackStudy.captureEvents(agent, demo, run, "startup", FIXTURES + "SafeDemoApplication",
                List.of(), "MONITOR", "");
        var startupEvent = startup.stream().filter(event -> "ALERT".equals(event.path("decision").asText())
                        && !event.path("enforced").asBoolean())
                .findFirst().orElseThrow(() -> new IOException("The monitored startup did not yield an advisory alert."));
        Files.createFile(run.resolve("startup.done"));
        var blocked = StackStudy.captureEvents(agent, demo, run, "blocked", FIXTURES + "DeserializationDemo",
                List.of("blocked"), "BLOCK", "");
        var denied = blocked.stream().filter(event -> event.path("enforced").asBoolean()).findFirst()
                .orElseThrow(() -> new IOException("The denylisted fixture was not blocked. See blocked.log."));
        Files.createFile(run.resolve("blocked.done"));
        var nominal = StackStudy.captureEvents(agent, demo, run, "nominal", cache, "nominal", "");

        ObjectMapper mapper = new ObjectMapper();
        Path profileFile = run.resolve("profile.json");
        OrderedPathProfile.main(new String[]{"train", profileFile.toString(), run.resolve("nominal.jsonl").toString()});
        var profile = mapper.readValue(profileFile.toFile(), OrderedPathProfile.class);
        Files.createFile(run.resolve("baseline.done"));

        var reordered = StackStudy.captureEvents(agent, demo, run, "sequence", cache, "sequence", "");
        int known = 0;
        int unseen = 0;
        String example = null;
        OrderedPathProfile.VirtualPath exampleTransition = null;
        var previous = new HashMap<Long, OrderedPathProfile.MethodPath>();
        for (JsonNode event : reordered) {
            var path = OrderedPathProfile.path(event, true);
            long thread = event.required("threadId").asLong();
            var transition = OrderedPathProfile.VirtualPath.between(previous.put(thread, path), path);
            boolean familiar = "KNOWN".equals(profile.classify(path));
            if (familiar) {
                known++;
            }
            if (!profile.transitions().contains(transition)) {
                unseen++;
                if (familiar && example == null) {
                    example = event.required("eventId").asText();
                    exampleTransition = transition;
                }
            }
        }
        if (example == null) {
            throw new IOException("The changed sequence did not produce a known stack with an unseen transition.");
        }

        try (var writer = Files.newBufferedWriter(run.resolve("events.jsonl"))) {
            for (List<JsonNode> capture : List.of(startup, blocked, nominal, reordered)) {
                for (JsonNode event : capture) {
                    writer.write(mapper.writeValueAsString(event));
                    writer.newLine();
                }
            }
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(run.resolve("viva-report.json").toFile(), Map.of(
                "startupCount", startup.size(),
                "startupEvent", startupEvent.required("eventId").asText(),
                "blockedEvent", denied.required("eventId").asText(),
                "nominalCount", nominal.size(),
                "sequenceCount", reordered.size(),
                "knownStacks", known,
                "unseenTransitions", unseen,
                "sequenceEvent", example,
                "transition", exampleTransition));
        System.out.println("Viva evidence written to " + run);
    }
}
