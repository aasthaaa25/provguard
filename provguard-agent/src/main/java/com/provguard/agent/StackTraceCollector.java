package com.provguard.agent;

import com.provguard.core.CallFrame;
import com.provguard.core.PathSignatures;
import com.provguard.core.SecurityEventFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects the stack frames that are active at a monitored call.
 * The result is a stack-based call-path trace.
 */
public final class StackTraceCollector {
    private StackTraceCollector() {
    }

    public record CapturedStack(
            List<CallFrame> frames,
            SecurityEventFactory.StackCounts counts,
            String pathSignature,
            String pathFamily) {
    }

    public static CapturedStack capture(AgentConfig config, String sinkLabel) {
        List<CallFrame> frames = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(stream -> stream.limit(48)
                        .map(frame -> toFrame(frame, config))
                        .filter(frame -> frame != null)
                        .toList());
        int application = 0;
        int jdk = 0;
        int external = 0;
        boolean reflection = false;
        Set<String> packages = new HashSet<>();
        Set<String> loaders = new HashSet<>();
        for (CallFrame frame : frames) {
            if (!frame.packageName().isBlank()) {
                packages.add(frame.packageName());
            }
            loaders.add(frame.classLoaderName());
            if (isReflection(frame)) {
                reflection = true;
            }
            if (frame.applicationClass()) {
                application++;
            } else if (AgentConfig.isPlatform(frame.className())) {
                jdk++;
            } else {
                external++;
            }
        }
        return new CapturedStack(
                frames,
                new SecurityEventFactory.StackCounts(
                        frames.size(),
                        application,
                        jdk,
                        external,
                        packages.size(),
                        loaders.size(),
                        reflection),
                PathSignatures.signature(frames, sinkLabel),
                PathSignatures.family(frames));
    }

    private static CallFrame toFrame(StackWalker.StackFrame frame, AgentConfig config) {
        String className = frame.getClassName();
        if (className.startsWith("com.provguard.") && !className.startsWith("com.provguard.demo.")) {
            return null;
        }
        if (className.startsWith("java.lang.reflect.MethodAccessor")
                || className.startsWith("jdk.internal.reflect.")) {
            return new CallFrame(className, frame.getMethodName(), "", -1, "java.base", "bootstrap", false);
        }
        String loaderName = "UNKNOWN_CLASS_LOADER";
        String moduleName = "UNKNOWN_MODULE";
        try {
            Class<?> type = frame.getDeclaringClass();
            ClassLoader loader = type.getClassLoader();
            if (loader == null) {
                loaderName = "bootstrap";
            } else {
                String name = loader.getName();
                loaderName = name == null || name.isBlank() ? loader.getClass().getName() : name;
            }
            Module module = type.getModule();
            if (module == null) {
                moduleName = "UNKNOWN_MODULE";
            } else if (!module.isNamed()) {
                moduleName = "UNNAMED_MODULE";
            } else {
                moduleName = module.getName();
            }
        } catch (Throwable ignored) {
            loaderName = "UNKNOWN_CLASS_LOADER";
            moduleName = "UNKNOWN_MODULE";
        }
        boolean application = config.isApplicationClass(className, loaderName);
        return new CallFrame(
                className,
                frame.getMethodName(),
                frame.getFileName() == null ? "" : frame.getFileName(),
                frame.getLineNumber(),
                moduleName,
                loaderName,
                application);
    }

    private static boolean isReflection(CallFrame frame) {
        return frame.className().startsWith("java.lang.reflect.")
                || frame.className().startsWith("jdk.internal.reflect.")
                || frame.className().contains("MethodAccessor");
    }
}
