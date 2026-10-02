package com.provguard.core;

/**
 * High-risk Java API families ProvGuard knows how to describe.
 * Only the first three values are monitored in this version.
 */
public enum SinkType {
    RUNTIME_EXEC,
    PROCESS_BUILDER_START,
    DESERIALIZATION_RESOLVE_CLASS,
    JNDI_LOOKUP,
    SCRIPT_ENGINE_EVAL;

    public boolean mandatory() {
        return this == RUNTIME_EXEC
                || this == PROCESS_BUILDER_START
                || this == DESERIALIZATION_RESOLVE_CLASS;
    }

    public int encoded() {
        return switch (this) {
            case RUNTIME_EXEC -> 0;
            case PROCESS_BUILDER_START -> 1;
            case DESERIALIZATION_RESOLVE_CLASS -> 2;
            case JNDI_LOOKUP -> 3;
            case SCRIPT_ENGINE_EVAL -> 4;
        };
    }
}
