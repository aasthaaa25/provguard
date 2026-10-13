package com.provguard.ml;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsolationForestTest {

    @Test
    void suspiciousFamiliesScoreHigherThanTheHeldOutBenignFamily() {
        IsolationForestTrainer.Result result = IsolationForestTrainer.train(new Random(42));
        assertTrue(result.suspiciousMean() > result.benignMean(), result.summary());
        assertTrue(result.recall() >= 0.8, result.summary());
        assertTrue(result.report().contains("katehighnam/beth-dataset"), result.summary());
        assertTrue(result.model().trees().length == 100);
    }

    @Test
    void liveSafeShapesScoreBelowTheAlertThreshold() {
        IsolationForestModel model = IsolationForestTrainer.train(new Random(42)).model();
        int echo = model.score(IsolationForestTrainer.SAFE_ECHO);
        int runtime = model.score(IsolationForestTrainer.SAFE_RUNTIME);
        int delegated = model.score(IsolationForestTrainer.SAFE_DELEGATED);
        int deserialize = model.score(IsolationForestTrainer.SAFE_DESERIALIZE);
        String scores = "echo=" + echo + " runtime=" + runtime
                + " delegated=" + delegated + " deserialize=" + deserialize;
        assertTrue(echo < 80, scores);
        assertTrue(runtime < 80, scores);
        assertTrue(delegated < 80, scores);
        assertTrue(deserialize < 80, scores);
    }

    @Test
    void modelRoundTripsThroughJson() throws Exception {
        IsolationForestModel trained = IsolationForestTrainer.train(new Random(42)).model();
        java.nio.file.Path file = java.nio.file.Files.createTempFile("provguard-iforest", ".json");
        try (var output = java.nio.file.Files.newOutputStream(file)) {
            trained.write(output);
        }
        IsolationForestModel loaded;
        try (var input = java.nio.file.Files.newInputStream(file)) {
            loaded = IsolationForestModel.read(input);
        }
        double[] row = {6, 2, 2, 0, 2, 0, 20, 0, 1, 0, 1, 4};
        assertEquals(trained.score(row), loaded.score(row));
    }
}
