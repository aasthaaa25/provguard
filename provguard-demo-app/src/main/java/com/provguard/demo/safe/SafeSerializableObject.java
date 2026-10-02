package com.provguard.demo.safe;

import java.io.Serial;
import java.io.Serializable;

public final class SafeSerializableObject implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String label;

    public SafeSerializableObject(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
