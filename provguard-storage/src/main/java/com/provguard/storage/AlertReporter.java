package com.provguard.storage;

import com.provguard.core.Decision;
import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;

public final class AlertReporter implements SecurityEventObserver {
    @Override
    public void onEvent(SecurityEvent event, RiskResult result) {
        if (result.decision() != Decision.ALERT && result.policyDecision() != Decision.BLOCK) {
            return;
        }
        System.out.println("PROVGUARD ALERT eventId=" + event.eventId()
                + " risk=" + result.riskLevel()
                + " ruleScore=" + result.ruleScore()
                + " mlScore=" + result.mlScore()
                + " sink=" + event.sinkMethod());
    }
}
