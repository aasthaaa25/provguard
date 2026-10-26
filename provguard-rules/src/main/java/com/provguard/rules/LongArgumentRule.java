package com.provguard.rules;

import com.provguard.core.SecurityEvent;

public final class LongArgumentRule extends AbstractRiskRule {
    static final int LIMIT = 180;

    public LongArgumentRule() {
        super("LONG_ARGUMENT");
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (event.argumentLength() < LIMIT) {
            return unmatched();
        }
        return matched(10, "Argument length is very long");
    }
}
