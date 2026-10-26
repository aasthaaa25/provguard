package com.provguard.agent;

public final class RecursionGuard {
    private static final ThreadLocal<Boolean> IN_AGENT_PROCESSING = ThreadLocal.withInitial(() -> false);

    private RecursionGuard() {
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(IN_AGENT_PROCESSING.get());
    }

    public static void run(Runnable action) {
        if (isActive()) {
            return;
        }
        IN_AGENT_PROCESSING.set(true);
        try {
            action.run();
        } finally {
            IN_AGENT_PROCESSING.remove();
        }
    }
}
