package com.provguard.rules;

import com.provguard.core.SecurityEvent;

public final class SpecialCharacterRule extends AbstractRiskRule {
    public SpecialCharacterRule() {
        super("SPECIAL_CHARACTER_DENSITY");
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (event.specialCharacterCount() < 8) {
            return unmatched();
        }
        return matched(10, "High special-character count");
    }
}
