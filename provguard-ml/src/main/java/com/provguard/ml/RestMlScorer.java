package com.provguard.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.provguard.core.exception.ModelUnavailableException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class RestMlScorer implements MlScorer {
    private final URI endpoint;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public RestMlScorer(String endpoint) {
        this.endpoint = URI.create(endpoint);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(200)).build();
    }

    @Override
    public MlPredictionResult score(FeatureVector features, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"features\":" + features.toJson() + "}"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ModelUnavailableException("ML service returned HTTP " + response.statusCode());
            }
            JsonNode node = mapper.readTree(response.body());
            int score = node.path("mlScore").asInt(0);
            return MlPredictionResult.available(score, "REST");
        } catch (ModelUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelUnavailableException("ML service is unavailable", exception);
        }
    }
}
