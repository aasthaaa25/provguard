package com.provguard.rules;

import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;

import java.util.Locale;

public final class JndiProtocolRule extends AbstractRiskRule {
    public JndiProtocolRule() {
        super("UNSAFE_JNDI_PROTOCOL");
    }

    @Override
    public RuleEvaluation evaluate(SecurityEvent event) {
        if (event.sinkType() != SinkType.JNDI_LOOKUP) {
            return unmatched();
        }
        String preview = event.redactedArgumentPreview();
        String lower = preview == null ? "" : preview.toLowerCase(Locale.ROOT);
        if (event.urlLike() || lower.startsWith("ldap:") || lower.startsWith("rmi:")
                || lower.startsWith("dns:") || lower.startsWith("iiop:") || lower.startsWith("corbaname:")
                || lower.startsWith("http:") || lower.startsWith("https:")) {
            return matched(80, "Remote or untrusted JNDI protocol in lookup target");
        }
        return unmatched();
    }
}
