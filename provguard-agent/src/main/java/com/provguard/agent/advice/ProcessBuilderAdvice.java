package com.provguard.agent.advice;

import com.provguard.agent.RuntimeBridge;
import com.provguard.core.exception.ProvGuardBlockedException;
import net.bytebuddy.asm.Advice;

import java.util.List;

public final class ProcessBuilderAdvice {
    private ProcessBuilderAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.FieldValue("command") List<String> command) {
        try {
            RuntimeBridge.onProcessBuilderStart(command);
        } catch (ProvGuardBlockedException blocked) {
            throw blocked;
        } catch (Throwable ignored) {
            // Fail open: an agent defect must not break the target application.
        }
    }
}
