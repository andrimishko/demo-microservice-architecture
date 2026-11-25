package ua.lab3.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;

/** Request/response bodies of the REST API (except FeedbackRequest). */
public final class Dtos {
    private Dtos() {}

    public record MissedRequest(
            @JsonProperty("node_id") @NotBlank String nodeId,
            @NotNull Instant from,
            @NotNull Instant to,
            @Size(max = 1000) String comment) {}

    public record MissedResult(
            @JsonProperty("feedback_ids") List<String> feedbackIds,
            @JsonProperty("marked_analyses") int markedAnalyses) {}

    public record SuggestionRequest(@NotBlank @Size(max = 2000) String text) {}

    public record FeedbackView(
            @JsonProperty("feedback_id") String feedbackId,
            @JsonProperty("analysis_id") String analysisId,
            @JsonProperty("node_id") String nodeId,
            Verdict verdict,
            Cause cause,
            String comment,
            String author,
            @JsonProperty("created_at") Instant createdAt) {
        static FeedbackView of(FeedbackEntity f) {
            return new FeedbackView(f.feedbackId(), f.analysisId(), f.nodeId(), f.verdict(), f.cause(), f.comment(),
                    f.author(), f.createdAt());
        }
    }

    public record Stats(
            long alerts,
            long confirmed,
            @JsonProperty("false_positive") long falsePositive,
            long missed,
            @JsonProperty("fp_rate") double fpRate,
            double precision,
            @JsonProperty("top_causes") Map<String, Long> topCauses) {

        /** alerts = number of non-NORMAL snapshots in the period; the rest is counted from the period's feedback. */
        static Stats of(long alerts, List<FeedbackEntity> feedback) {
            long confirmed = count(feedback, Verdict.CONFIRMED), fp = count(feedback, Verdict.FALSE_POSITIVE);
            long missed = count(feedback, Verdict.MISSED), judged = confirmed + fp;
            Map<String, Long> causes = feedback.stream().filter(f -> f.verdict() == Verdict.FALSE_POSITIVE && f.cause() != null)
                    .collect(Collectors.groupingBy(f -> f.cause().name(), Collectors.counting()));
            Map<String, Long> top = new LinkedHashMap<>();
            causes.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(3)
                    .forEach(e -> top.put(e.getKey(), e.getValue()));
            return new Stats(alerts, confirmed, fp, missed, judged == 0 ? 0 : (double) fp / judged,
                    judged == 0 ? 0 : (double) confirmed / judged, top);
        }

        private static long count(List<FeedbackEntity> feedback, Verdict v) {
            return feedback.stream().filter(f -> f.verdict() == v).count();
        }
    }
}
