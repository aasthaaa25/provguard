package com.provguard.rules;

import com.provguard.core.SecurityEvent;

public final class CommandPatternRule extends AbstractRiskRule {
    public CommandPatternRule() {
        super("SUSPICIOUS_COMMAND_PATTERN");
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (!event.commandLike()) {
            return unmatched();
        }
        return matched(15, "Command-like pattern found in safe metadata");
    }
}
