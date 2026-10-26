package com.provguard.rules;

import com.provguard.core.CallFrame;
import com.provguard.core.SecurityEvent;

import java.util.Set;

public final class UnknownPathRule extends AbstractRiskRule {
    private final Set<String> knownPackages;
    private final Set<String> knownSignatures;

    public UnknownPathRule(Set<String> knownPackages, Set<String> knownSignatures) {
        super("UNKNOWN_PATH_SIGNATURE");
        this.knownPackages = Set.copyOf(knownPackages);
        this.knownSignatures = Set.copyOf(knownSignatures);
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (knownSignatures.contains(event.pathSignature()) || knownPackage(event)) {
            return unmatched();
        }
        return matched(20, "Call-path signature is not in the configured known set");
    }

    private boolean knownPackage(SecurityEvent event) {
        boolean sawApplication = false;
        for (CallFrame frame : event.callFrames()) {
            if (!frame.applicationClass()) {
                continue;
            }
            sawApplication = true;
            boolean known = false;
            for (String prefix : knownPackages) {
                String normalized = prefix.endsWith(".") ? prefix : prefix + ".";
                if (frame.className().startsWith(normalized)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                return false;
            }
        }
        return sawApplication;
    }
}
