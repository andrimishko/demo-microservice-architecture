package ua.lab3.common.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Payload of feedback.submitted: the operator's verdict on one analysis, with the vector that was scored. */
public record FeedbackSubmitted(
        @JsonProperty("feedback_id") String feedbackId,
        @JsonProperty("analysis_id") String analysisId,
        @JsonProperty("node_id") String nodeId,
        Verdict verdict,
        String cause,
        List<Double> vector,
        @JsonProperty("feature_names") List<String> featureNames) {

    public enum Verdict { CONFIRMED, FALSE_POSITIVE, MISSED }
}
