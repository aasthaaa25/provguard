package com.provguard.agent.advice;

import com.provguard.agent.RuntimeBridge;
import com.provguard.core.exception.ProvGuardBlockedException;
import net.bytebuddy.asm.Advice;

import java.io.ObjectStreamClass;

public final class ResolveClassAdvice {
    private ResolveClassAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(0) ObjectStreamClass descriptor) {
        try {
            RuntimeBridge.onResolveClass(descriptor == null ? null : descriptor.getName());
        } catch (ProvGuardBlockedException blocked) {
            throw blocked;
        } catch (Throwable ignored) {
            // Fail open: an agent defect must not break the target application.
        }
    }
}
