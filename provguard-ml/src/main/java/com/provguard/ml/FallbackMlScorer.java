package com.provguard.ml;

import java.time.Duration;

public final class FallbackMlScorer implements MlScorer {
    private final String detail;

    public FallbackMlScorer(String detail) {
        this.detail = detail;
    }

    @Override
    public MlPredictionResult score(FeatureVector features, Duration timeout) {
        return MlPredictionResult.unavailable(detail);
    }
}
