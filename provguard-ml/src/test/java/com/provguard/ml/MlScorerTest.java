package com.provguard.ml;

import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
    void outOfRangeEndpointResponseIsUnavailable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/score", exchange -> {
            byte[] body = "{\"mlScore\":101}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();
        try {
            MlScorer scorer = ResilientMlScorer.fromUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/score");
            assertFalse(scorer.score(sample(), Duration.ofSeconds(2)).available());
        } finally {
            server.stop(0);
        }
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
