package com.provguard.demo.unsafe;

import java.io.Serial;
import java.io.Serializable;

/**
 * Harmless marker class. Block mode denies it because the name is on the denylist.
 * It has no dangerous readObject behavior.
 */
public final class BlockedDemoObject implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String label;

    public BlockedDemoObject(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
