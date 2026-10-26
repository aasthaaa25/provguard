package com.provguard.agent;

import com.provguard.core.AgentMode;
import com.provguard.core.exception.InvalidAgentConfigurationException;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class AgentConfig {
    private final AgentMode mode;
    private final Path eventsFile;
    private final int queueCapacity;
    private final String mlUrl;
    private final Set<String> knownPackages;
    private final Set<String> appPackages;
    private final Set<String> knownSignatures;
    private final Set<String> deserializationAllowlist;
    private final Set<String> deserializationDenylist;
    private final int frequencyThreshold;
    private final boolean verbose;

    private AgentConfig(
            AgentMode mode,
            Path eventsFile,
            int queueCapacity,
            String mlUrl,
            Set<String> knownPackages,
            Set<String> appPackages,
            Set<String> knownSignatures,
            Set<String> deserializationAllowlist,
            Set<String> deserializationDenylist,
            int frequencyThreshold,
            boolean verbose) {
        this.mode = mode;
        this.eventsFile = eventsFile;
        this.queueCapacity = queueCapacity;
        this.mlUrl = mlUrl;
        this.knownPackages = knownPackages;
        this.appPackages = appPackages;
        this.knownSignatures = knownSignatures;
        this.deserializationAllowlist = deserializationAllowlist;
        this.deserializationDenylist = deserializationDenylist;
        this.frequencyThreshold = frequencyThreshold;
        this.verbose = verbose;
    }

    public static AgentConfig load(String agentArgs) {
        try {
            return parse(agentArgs);
        } catch (InvalidAgentConfigurationException exception) {
            System.err.println("ProvGuard configuration error. Using monitor-mode defaults. " + exception.getMessage());
            return defaults();
        }
    }

    public static AgentConfig defaults() {
        return parse("mode=MONITOR");
    }

    public static AgentConfig parse(String agentArgs) {
        String merged = mergeWithSystemProperties(agentArgs);
        AgentMode mode = AgentMode.MONITOR;
        Path eventsFile = Path.of("events.jsonl");
        int queueCapacity = 1024;
        String mlUrl = null;
        Set<String> knownPackages = setOf("com.provguard.demo.safe");
        Set<String> appPackages = setOf("com.provguard.demo");
        Set<String> knownSignatures = Set.of();
        Set<String> allowlist = setOf("com.provguard.demo.safe.SafeSerializableObject");
        Set<String> denylist = setOf("com.provguard.demo.unsafe.BlockedDemoObject");
        int frequencyThreshold = 5;
        boolean verbose = true;

        if (merged != null && !merged.isBlank()) {
            for (String part : merged.split(";")) {
                if (part.isBlank()) {
                    continue;
                }
                int eq = part.indexOf('=');
                if (eq < 1) {
                    throw new InvalidAgentConfigurationException("Expected key=value but found " + part);
                }
                String key = part.substring(0, eq).trim();
                String value = part.substring(eq + 1).trim();
                switch (key) {
                    case "mode" -> {
                        try {
                            mode = AgentMode.parse(value);
                        } catch (IllegalArgumentException exception) {
                            throw new InvalidAgentConfigurationException("Unknown mode " + value);
                        }
                    }
                    case "events" -> eventsFile = Path.of(value);
                    case "queueCapacity" -> queueCapacity = positive(value, "queueCapacity");
                    case "mlUrl" -> mlUrl = value.isBlank() ? null : value;
                    case "knownPackages" -> knownPackages = setOf(value.split(","));
                    case "appPackages" -> appPackages = setOf(value.split(","));
                    case "knownSignatures" -> knownSignatures = setOf(value.split(","));
                    case "allowlist" -> allowlist = setOf(value.split(","));
                    case "denylist" -> denylist = setOf(value.split(","));
                    case "frequencyThreshold" -> frequencyThreshold = positive(value, "frequencyThreshold");
                    case "verbose" -> verbose = Boolean.parseBoolean(value);
                    default -> throw new InvalidAgentConfigurationException("Unknown configuration key " + key);
                }
            }
        }
        return new AgentConfig(
                mode,
                eventsFile,
                queueCapacity,
                mlUrl,
                knownPackages,
                appPackages,
                knownSignatures,
                allowlist,
                denylist,
                frequencyThreshold,
                verbose);
    }

    public boolean isApplicationClass(String className, String loaderName) {
        for (String prefix : appPackages) {
            if (className.startsWith(normalize(prefix))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPlatform(String className) {
        return className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("com.sun.");
    }

    public AgentMode mode() {
        return mode;
    }

    public Path eventsFile() {
        return eventsFile;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public String mlUrl() {
        return mlUrl;
    }

    public Set<String> knownPackages() {
        return knownPackages;
    }

    public Set<String> appPackages() {
        return appPackages;
    }

    public Set<String> knownSignatures() {
        return knownSignatures;
    }

    public Set<String> deserializationAllowlist() {
        return deserializationAllowlist;
    }

    public Set<String> deserializationDenylist() {
        return deserializationDenylist;
    }

    public int frequencyThreshold() {
        return frequencyThreshold;
    }

    public boolean verbose() {
        return verbose;
    }

    private static int positive(String value, String key) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new InvalidAgentConfigurationException(key + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new InvalidAgentConfigurationException(key + " is not an integer");
        }
    }

    private static Set<String> setOf(String... values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                set.add(value.trim());
            }
        }
        return Set.copyOf(set);
    }

    private static String normalize(String prefix) {
        return prefix.endsWith(".") ? prefix : prefix + ".";
    }

    private static String mergeWithSystemProperties(String agentArgs) {
        StringBuilder builder = new StringBuilder(agentArgs == null ? "" : agentArgs);
        copyProperty(builder, "provguard.mode", "mode");
        copyProperty(builder, "provguard.events", "events");
        copyProperty(builder, "provguard.queueCapacity", "queueCapacity");
        copyProperty(builder, "provguard.mlUrl", "mlUrl");
        copyProperty(builder, "provguard.verbose", "verbose");
        return builder.toString();
    }

    private static void copyProperty(StringBuilder builder, String property, String key) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ';') {
            builder.append(';');
        }
        builder.append(key).append('=').append(value.trim());
    }

    @Override
    public String toString() {
        return "mode=" + mode
                + " events=" + eventsFile
                + " queueCapacity=" + queueCapacity
                + " mlUrl=" + (mlUrl == null ? "disabled" : mlUrl)
                + " verbose=" + verbose;
    }
}
