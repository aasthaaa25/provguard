package com.provguard.ml;

import java.time.Duration;

public interface MlScorer {
    MlPredictionResult score(FeatureVector features, Duration timeout);
}
