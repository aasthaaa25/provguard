package com.provguard.agent.advice;

import com.provguard.agent.RuntimeBridge;
import com.provguard.core.exception.ProvGuardBlockedException;
import net.bytebuddy.asm.Advice;

public final class RuntimeExecAdvice {
    private RuntimeExecAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.AllArguments Object[] args) {
        try {
            RuntimeBridge.onRuntimeExec(args);
        } catch (ProvGuardBlockedException blocked) {
            throw blocked;
        } catch (Throwable ignored) {
            // Fail open: an agent defect must not break the target application.
        }
    }
}
