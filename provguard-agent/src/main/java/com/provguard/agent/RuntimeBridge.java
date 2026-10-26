package com.provguard.agent;

import java.util.List;

/**
 * Entry point used by inlined Byte Buddy advice.
 * This class is loaded by the bootstrap class loader so the instrumented JDK methods and the agent share one copy.
 */
public final class RuntimeBridge {
    private static volatile EventPipeline pipeline;

    private RuntimeBridge() {
    }

    public static void initialize(String agentArgs) {
        if (pipeline != null) {
            return;
        }
        AgentConfig config = AgentConfig.load(agentArgs);
        pipeline = EventPipeline.start(config);
        System.out.println("ProvGuard configuration " + config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            EventPipeline current = pipeline;
            if (current != null) {
                current.close();
            }
        }, "provguard-shutdown"));
    }

    public static void onRuntimeExec(Object[] args) {
        EventPipeline current = pipeline;
        if (current != null) {
            current.onRuntimeExec(args);
        }
    }

    public static void onProcessBuilderStart(List<String> command) {
        EventPipeline current = pipeline;
        if (current != null) {
            current.onProcessBuilder(command);
        }
    }

    public static void onResolveClass(String className) {
        EventPipeline current = pipeline;
        if (current != null) {
            current.onResolveClass(className);
        }
    }
}
