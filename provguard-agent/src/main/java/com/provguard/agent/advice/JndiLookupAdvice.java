package com.provguard.agent.advice;

import com.provguard.agent.RuntimeBridge;
import com.provguard.core.exception.ProvGuardBlockedException;
import net.bytebuddy.asm.Advice;

public final class JndiLookupAdvice {
    private JndiLookupAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(0) Object name) {
        try {
            RuntimeBridge.onJndiLookup(name);
        } catch (ProvGuardBlockedException blocked) {
            throw blocked;
        } catch (Throwable ignored) {
            // Fail open: an agent defect must not break the target application.
        }
    }
}
