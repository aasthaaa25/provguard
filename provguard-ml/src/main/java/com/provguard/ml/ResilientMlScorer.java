package com.provguard.ml;

import java.time.Duration;

/**
 * Prefers the configured scorer and falls back to the next one when that scorer fails.
 */
public final class ResilientMlScorer implements MlScorer {
    private final MlScorer primary;
    private final MlScorer fallback;

    public ResilientMlScorer(MlScorer primary, MlScorer fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    public static MlScorer create(String mlUrl) {
        MlScorer local = bundled();
        if (mlUrl == null || mlUrl.isBlank()) {
            return local;
        }
        return new ResilientMlScorer(new RestMlScorer(mlUrl), local);
    }

    public static MlScorer fromUrl(String mlUrl) {
        if (mlUrl == null || mlUrl.isBlank()) {
            return new FallbackMlScorer("No ML endpoint configured");
        }
        return new ResilientMlScorer(new RestMlScorer(mlUrl), new FallbackMlScorer("ML request failed"));
    }

    private static MlScorer bundled() {
        IsolationForestModel model = IsolationForestModel.bundled();
        if (model == null) {
            return new FallbackMlScorer("Java Isolation Forest model is not packaged");
        }
        return new IsolationForestScorer(model);
    }

    @Override
    public MlPredictionResult score(FeatureVector features, Duration timeout) {
        try {
            MlPredictionResult result = primary.score(features, timeout);
            if (result.available()) {
                return result;
            }
        } catch (RuntimeException ignored) {
            // The fallback scorer decides.
        }
        return fallback.score(features, timeout);
    }
}
