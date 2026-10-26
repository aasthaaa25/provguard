package com.provguard.ml;

import java.time.Duration;

/**
 * Scores a call-path feature vector with the Isolation Forest trained in this module.
 */
public final class IsolationForestScorer implements MlScorer {
    private final IsolationForestModel model;

    public IsolationForestScorer(IsolationForestModel model) {
        this.model = model;
    }

    @Override
    public MlPredictionResult score(FeatureVector features, Duration timeout) {
        return MlPredictionResult.available(model.score(features.values()), "java-isolation-forest");
    }
}
