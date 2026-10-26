package com.provguard.ml;

public record MlPredictionResult(int score, String classification, String modelStatus, String detail) {
    public static MlPredictionResult unavailable(String detail) {
        return new MlPredictionResult(0, "UNAVAILABLE", "ML_UNAVAILABLE", detail == null ? "" : detail);
    }

    public static MlPredictionResult available(int score, String detail) {
        int bounded = Math.max(0, Math.min(100, score));
        return new MlPredictionResult(bounded, classify(bounded), "AVAILABLE", detail == null ? "" : detail);
    }

    public boolean available() {
        return "AVAILABLE".equals(modelStatus);
    }

    public static String classify(int score) {
        if (score >= 80) {
            return "HIGHLY_UNUSUAL";
        }
        if (score >= 40) {
            return "UNUSUAL";
        }
        return "NORMAL";
    }
}
