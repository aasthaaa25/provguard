package com.provguard.rules;

import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;

import java.util.Locale;

public final class ScriptInjectionRule extends AbstractRiskRule {
    public ScriptInjectionRule() {
        super("UNSAFE_SCRIPT_EVAL");
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (event.sinkType() != SinkType.SCRIPT_ENGINE_EVAL) {
            return unmatched();
        }
        String preview = event.redactedArgumentPreview();
        String lower = preview == null ? "" : preview.toLowerCase(Locale.ROOT);
        if (event.commandLike() || event.suspiciousKeywordCount() > 0
                || lower.contains("runtime") || lower.contains("process") || lower.contains("exec")
                || lower.contains("system.exit")) {
            return matched(80, "Dangerous execution pattern in script evaluation");
        }
        return unmatched();
    }
}
