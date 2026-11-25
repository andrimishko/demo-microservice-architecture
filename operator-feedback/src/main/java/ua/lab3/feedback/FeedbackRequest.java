package ua.lab3.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;

public record FeedbackRequest(
        @JsonProperty("analysis_id") @NotNull String analysisId,
        @NotNull Verdict verdict,
        Cause cause,
        @Size(max = 1000) String comment) {}
