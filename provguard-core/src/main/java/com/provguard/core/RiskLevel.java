package com.provguard.core;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static RiskLevel fromScore(int score) {
        int bounded = Math.max(0, Math.min(100, score));
        if (bounded >= 80) {
            return CRITICAL;
        }
        if (bounded >= 60) {
            return HIGH;
        }
        if (bounded >= 30) {
            return MEDIUM;
        }
        return LOW;
    }
}
