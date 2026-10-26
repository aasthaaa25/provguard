package com.provguard.rules;

import com.provguard.core.SecurityEvent;

public final class ReflectionRule extends AbstractRiskRule {
    public ReflectionRule() {
        super("REFLECTION_IN_CALL_PATH");
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (!event.reflectionDetected()) {
            return unmatched();
        }
        return matched(15, "Reflection present in the call-path trace");
    }
}
