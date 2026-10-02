package com.provguard.ml;

import com.provguard.core.SecurityEvent;

public final class FeatureExtractor {
    public FeatureVector extract(SecurityEvent event, int ruleScore) {
        return FeatureVector.from(event, ruleScore);
    }
}
