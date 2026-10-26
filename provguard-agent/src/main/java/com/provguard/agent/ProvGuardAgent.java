package com.provguard.agent;

import java.lang.instrument.Instrumentation;

public final class ProvGuardAgent {
    private ProvGuardAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        AgentInstaller.install(agentArgs, instrumentation);
    }
}
