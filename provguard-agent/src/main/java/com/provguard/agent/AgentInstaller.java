package com.provguard.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Installs Byte Buddy advice. This class stays on the system class loader and does not call the runtime bridge directly.
 */
public final class AgentInstaller {
    private AgentInstaller() {
    }

    public static void install(String agentArgs, Instrumentation instrumentation) {
        try {
            File agentJar = locateAgentJar();
            File runtimeJar = runtimeJar(agentJar);
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(runtimeJar));
            Class<?> bridge = Class.forName("com.provguard.agent.RuntimeBridge", true, null);
            bridge.getMethod("initialize", String.class).invoke(null, agentArgs);
            int installed = 0;
            installed += install(instrumentation, "Runtime.exec", "java.lang.Runtime",
                    "com.provguard.agent.advice.RuntimeExecAdvice", "exec", -1);
            installed += install(instrumentation, "ProcessBuilder.start", "java.lang.ProcessBuilder",
                    "com.provguard.agent.advice.ProcessBuilderAdvice", "start", 0);
            installed += install(instrumentation, "ObjectInputStream.resolveClass", "java.io.ObjectInputStream",
                    "com.provguard.agent.advice.ResolveClassAdvice", "resolveClass", 1);
            if (installed > 0) {
                System.out.println("ProvGuard installed sinks: " + installed);
                System.out.println("ProvGuard Agent Started Successfully");
            } else {
                System.err.println("ProvGuard could not instrument any sink. Target application will continue.");
            }
        } catch (Throwable failure) {
            System.err.println("ProvGuard agent initialization failed. Target application will continue.");
            failure.printStackTrace(System.err);
        }
    }

    private static int install(
            Instrumentation instrumentation,
            String sinkName,
            String typeName,
            String adviceName,
            String methodName,
            int argumentCount) {
        try {
            ElementMatcher.Junction<MethodDescription> named = ElementMatchers.named(methodName);
            ElementMatcher.Junction<MethodDescription> method = argumentCount >= 0
                    ? named.and(ElementMatchers.takesArguments(argumentCount))
                    : named;
            Class<?> advice = Class.forName(adviceName, true, AgentInstaller.class.getClassLoader());
            new AgentBuilder.Default()
                    .disableClassFormatChanges()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                    .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .ignore(ElementMatchers.nameStartsWith("net.bytebuddy.")
                            .or(ElementMatchers.nameStartsWith("com.provguard.agent."))
                            .or(ElementMatchers.nameStartsWith("com.provguard.rules."))
                            .or(ElementMatchers.nameStartsWith("com.provguard.storage."))
                            .or(ElementMatchers.nameStartsWith("com.provguard.ml."))
                            .or(ElementMatchers.nameStartsWith("com.provguard.core."))
                            .or(ElementMatchers.nameStartsWith("com.fasterxml."))
                            .or(ElementMatchers.nameStartsWith("org.slf4j."))
                            .or(ElementMatchers.nameStartsWith("ch.qos.logback.")))
                    .with(new AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onError(
                                String instrumented,
                                ClassLoader classLoader,
                                net.bytebuddy.utility.JavaModule module,
                                boolean loaded,
                                Throwable throwable) {
                            System.err.println("ProvGuard could not instrument " + instrumented + ": " + throwable.getMessage());
                        }
                    })
                    .type(ElementMatchers.named(typeName))
                    .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                            builder.visit(Advice.to(advice).on(method)))
                    .installOn(instrumentation);
            System.out.println("ProvGuard monitoring " + sinkName);
            return 1;
        } catch (Throwable failure) {
            System.err.println("ProvGuard skipped sink " + sinkName + ". Remaining sinks will still be installed. "
                    + failure.getClass().getName() + ": " + failure.getMessage());
            return 0;
        }
    }

    private static File runtimeJar(File agentJar) throws IOException {
        File runtime = File.createTempFile("provguard-runtime", ".jar");
        runtime.deleteOnExit();
        try (JarFile source = new JarFile(agentJar);
                JarOutputStream output = new JarOutputStream(Files.newOutputStream(runtime.toPath()))) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !runtimeEntry(entry.getName())) {
                    continue;
                }
                output.putNextEntry(new JarEntry(entry.getName()));
                try (InputStream input = source.getInputStream(entry)) {
                    input.transferTo(output);
                }
                output.closeEntry();
            }
        }
        return runtime;
    }

    private static boolean runtimeEntry(String name) {
        if (name.startsWith("net/bytebuddy/")
                || name.equals("META-INF/MANIFEST.MF")
                || name.endsWith("module-info.class")) {
            return false;
        }
        if (name.startsWith("com/provguard/agent/")) {
            return name.startsWith("com/provguard/agent/RuntimeBridge")
                    || name.startsWith("com/provguard/agent/EventPipeline")
                    || name.startsWith("com/provguard/agent/AgentConfig")
                    || name.startsWith("com/provguard/agent/RecursionGuard")
                    || name.startsWith("com/provguard/agent/StackTraceCollector");
        }
        return name.startsWith("com/provguard/")
                || name.startsWith("com/fasterxml/")
                || name.startsWith("org/slf4j/")
                || name.startsWith("ch/qos/")
                || name.startsWith("META-INF/")
                || name.equals("logback.xml");
    }

    private static File locateAgentJar() throws URISyntaxException {
        var source = AgentInstaller.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new IllegalStateException("ProvGuard cannot locate its agent JAR");
        }
        File jar = new File(source.getLocation().toURI());
        if (!jar.isFile()) {
            throw new IllegalStateException("ProvGuard must be started with -javaagent:<shaded-jar>. Found " + jar);
        }
        return jar;
    }
}
