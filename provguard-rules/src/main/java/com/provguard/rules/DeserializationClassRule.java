package com.provguard.rules;

import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;

import java.util.Set;

public final class DeserializationClassRule extends AbstractRiskRule {
    private final Set<String> allowlist;
    private final Set<String> denylist;

    public DeserializationClassRule(Set<String> allowlist, Set<String> denylist) {
        super("DESERIALIZATION_CLASS");
        this.allowlist = Set.copyOf(allowlist);
        this.denylist = Set.copyOf(denylist);
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (event.sinkType() != SinkType.DESERIALIZATION_RESOLVE_CLASS) {
            return unmatched();
        }
        String className = event.requestedClassName();
        if (className == null || className.isBlank()) {
            return unmatched();
        }
        if (denylist.contains(className)) {
            return RuleEvaluation.match("UNSAFE_DESERIALIZATION_CLASS", 80, "Requested class is on the demo denylist");
        }
        if (allowlist.contains(className) || isPlatformClass(className)) {
            if (allowlist.contains(className)) {
                return RuleEvaluation.match("SAFE_DESERIALIZATION_ALLOWLIST", 0, "Requested class is on the allowlist");
            }
            return unmatched();
        }
        return RuleEvaluation.match("UNKNOWN_DESERIALIZATION_CLASS", 40, "Requested class is not on the allowlist");
    }

    private static boolean isPlatformClass(String className) {
        return className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("jdk.")
                || className.startsWith("[Ljava.")
                || className.startsWith("[Ljavax.");
    }
}
