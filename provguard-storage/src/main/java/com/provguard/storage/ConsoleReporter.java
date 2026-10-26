package com.provguard.storage;

import com.provguard.core.Decision;
import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsoleReporter implements SecurityEventObserver {
    private static final Logger LOG = logger();

    @Override
    public void onEvent(SecurityEvent event, RiskResult result) {
        String line = "ProvGuard " + result.decision()
                + " sink=" + event.sinkType()
                + " ruleScore=" + result.ruleScore()
                + " finalScore=" + result.finalScore()
                + " path=" + event.pathSignature()
                + " rules=" + result.matchedRules()
                + " model=" + result.modelStatus();
        if (result.decision() == Decision.ALLOW) {
            return;
        }
        if (LOG != null) {
            LOG.info(line);
        }
        System.out.println(line);
    }

    private static Logger logger() {
        try {
            return LoggerFactory.getLogger(ConsoleReporter.class);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
