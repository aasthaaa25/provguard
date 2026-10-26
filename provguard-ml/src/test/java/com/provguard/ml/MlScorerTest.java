package com.provguard.ml;

import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MlScorerTest {

    @Test
    void fallbackNeverLooksAvailable() {
        MlPredictionResult result = new FallbackMlScorer("offline").score(sample(), Duration.ofMillis(50));
        assertFalse(result.available());
        assertEquals("ML_UNAVAILABLE", result.modelStatus());
    }

    @Test
    void unreachableServiceFallsBack() {
        MlScorer scorer = ResilientMlScorer.fromUrl("http://127.0.0.1:9/score");
        MlPredictionResult result = scorer.score(sample(), Duration.ofMillis(200));
        assertFalse(result.available());
        assertEquals("ML_UNAVAILABLE", result.modelStatus());
    }

    @Test
    void classificationBands() {
        assertEquals("NORMAL", MlPredictionResult.classify(10));
        assertEquals("UNUSUAL", MlPredictionResult.classify(40));
        assertEquals("HIGHLY_UNUSUAL", MlPredictionResult.classify(80));
    }

    @Test
    void featureJsonUsesTheTrainingColumnNames() {
        String json = sample().toJson();
        assertTrue(json.contains("\"stack_depth\":"));
        assertTrue(json.contains("\"rule_score\":"));
        assertTrue(json.contains("\"sink_type_encoded\":0"));
    }

    private static FeatureVector sample() {
        SecurityEvent event = SecurityEvent.builder()
                .sinkType(SinkType.RUNTIME_EXEC)
                .stackDepth(4)
                .applicationFrameCount(2)
                .jdkFrameCount(2)
                .keywordRiskScore(35)
                .build();
        return new FeatureExtractor().extract(event, 35);
    }
}
