package ua.lab3.assessment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** Payload of state.classified; the same document is stored in the OpenSearch index analysis-results. */
public record Classified(
        @JsonProperty("analysis_id") String analysisId,
        @JsonProperty("node_id") String nodeId,
        @JsonProperty("window_start") String windowStart,
        State state,
        double score,
        @JsonProperty("model_version") int modelVersion,
        JsonNode vector,
        @JsonProperty("feature_names") JsonNode featureNames) {}
