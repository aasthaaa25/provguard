package com.provguard.desktop;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

record ReviewSettings(Path database, String title) {
    static ReviewSettings load() throws IOException {
        var values = new Properties();
        try (var input = ReviewSettings.class.getResourceAsStream("/review.properties")) {
            if (input == null) {
                throw new IOException("Missing bundled review.properties.");
            }
            values.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        String override = System.getProperty("provguard.review.config");
        if (override != null) {
            try (var reader = Files.newBufferedReader(Path.of(override))) {
                values.load(reader);
            }
        }
        return new ReviewSettings(Path.of(values.getProperty("database.path")), values.getProperty("ui.title"));
    }
}
