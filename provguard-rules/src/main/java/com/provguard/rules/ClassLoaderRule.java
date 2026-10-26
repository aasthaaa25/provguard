package com.provguard.rules;

import com.provguard.core.SecurityEvent;

public final class ClassLoaderRule extends AbstractRiskRule {
    public ClassLoaderRule() {
        super("NON_APPLICATION_CLASS_LOADER");
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (event.applicationFrameCount() > 0) {
            return unmatched();
        }
        if (event.stackDepth() == 0) {
            return unmatched();
        }
        return matched(10, "Call-path trace has no application frame");
    }
}
