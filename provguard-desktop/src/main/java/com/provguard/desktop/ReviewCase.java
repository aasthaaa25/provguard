package com.provguard.desktop;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

record ReviewCase(UUID id, LocalDateTime openedAt, String title, String source, Status status, String notes) {
    enum Status {
        OPEN("Open"), IN_REVIEW("In review"), CLOSED("Closed");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    ReviewCase {
        Objects.requireNonNull(id);
        Objects.requireNonNull(openedAt);
        Objects.requireNonNull(source);
        Objects.requireNonNull(status);
        title = Objects.requireNonNull(title).strip();
        notes = Objects.requireNonNull(notes);
        if (title.isEmpty() || title.length() > 160) {
            throw new IllegalArgumentException("Title must contain 1 to 160 characters.");
        }
        if (notes.length() > 4000) {
            throw new IllegalArgumentException("Notes must contain at most 4,000 characters.");
        }
    }
}
