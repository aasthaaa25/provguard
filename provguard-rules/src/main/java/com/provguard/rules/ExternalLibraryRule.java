package com.provguard.rules;

import com.provguard.core.SecurityEvent;

public final class ExternalLibraryRule extends AbstractRiskRule {
    public ExternalLibraryRule() {
        super("UNKNOWN_EXTERNAL_LIBRARY_RATIO");
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        int total = event.applicationFrameCount() + event.jdkFrameCount() + event.externalLibraryFrameCount();
        if (total == 0 || event.externalLibraryFrameCount() * 2 < total) {
            return unmatched();
        }
        return matched(10, "External library frames dominate the call-path trace");
    }
}
