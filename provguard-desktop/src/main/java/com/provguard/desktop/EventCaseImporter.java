package com.provguard.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

final class EventCaseImporter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private EventCaseImporter() {}

    static List<ReviewCase> read(Path file) throws ReviewException {
        List<ReviewCase> items = new ArrayList<>();
        var seen = new HashSet<UUID>();
        int lineNumber = 0;
        try (var reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                var event = JSON.readTree(line);
                var id = UUID.fromString(event.required("eventId").asText());
                if (seen.add(id)) {
                    var time = LocalDateTime.ofInstant(Instant.parse(event.required("timestamp").asText()),
                            ZoneId.systemDefault());
                    String sink = event.required("sinkType").asText();
                    String decision = event.required("decision").asText();
                    if (sink.isBlank() || decision.isBlank()) {
                        throw new IllegalArgumentException("Missing sink or decision");
                    }
                    items.add(new ReviewCase(id, time, sink + " · " + decision, "Agent event",
                            ReviewCase.Status.OPEN, ""));
                }
            }
            return items;
        } catch (IOException | RuntimeException exception) {
            String location = lineNumber == 0 ? "" : " at line " + lineNumber;
            throw new ReviewException("Could not read the event log" + location + ".", exception);
        }
    }
}
