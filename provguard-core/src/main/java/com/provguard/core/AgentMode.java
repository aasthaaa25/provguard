package com.provguard.core;

public enum AgentMode {
    MONITOR,
    ALERT,
    BLOCK;

    public static AgentMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Agent mode is empty");
        }
        return AgentMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
