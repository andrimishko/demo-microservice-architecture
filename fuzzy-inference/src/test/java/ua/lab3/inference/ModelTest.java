package ua.lab3.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;

class ModelTest {
    static Model seed() throws IOException {
        try (var in = ModelTest.class.getResourceAsStream("/seed-model.json")) {
            return Model.parse(in.readAllBytes());
        }
    }

    // vectors shaped like what feature-aggregator builds for the mock scenarios
    static final Map<String, Double> NORMAL = Map.of("cpu_usage", 0.26, "http_latency_p95_seconds", 0.15, "error_ratio", 0.0, "warn_ratio", 0.2);
    static final Map<String, Double> ANOMALY = Map.of("cpu_usage", 0.92, "http_latency_p95_seconds", 1.8, "error_ratio", 0.7, "warn_ratio", 0.2);
    static final Map<String, Double> NOISY = Map.of("cpu_usage", 0.26, "http_latency_p95_seconds", 0.15, "error_ratio", 0.0, "warn_ratio", 0.9);

    @Test
    void gaussianIsOneAtCenterAndDecays() {
        assertThat(Model.gaussian(0.5, 0.5, 0.2)).isEqualTo(1.0);
        assertThat(Model.gaussian(0.7, 0.5, 0.2)).isCloseTo(Math.exp(-0.5), org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void scoreIsWeightedAverageOfConsequents() throws IOException {
        // one rule per extreme of a single feature, x exactly halfway: equal firing, score = mean of consequents
        var low = new Model.Rule("A", List.of(new Model.Antecedent("f", Model.Term.LOW, 0, 0.5)), 0.2);
        var high = new Model.Rule("B", List.of(new Model.Antecedent("f", Model.Term.HIGH, 1, 0.5)), 0.8);
        Model m = new Model(1, 1, List.of("f"), Map.of("f", new Model.Bounds(0, 10)), List.of(low, high));
        assertThat(m.infer(Map.of("f", 5.0)).score()).isEqualTo(0.5);
        assertThat(m.infer(Map.of("f", 100.0)).score()).isGreaterThan(0.7); // clamped to 1 -> rule B dominates
    }

    @Test
    void missingFeatureContributesOneAndZeroFiringGivesZeroScore() throws IOException {
        Model m = seed();
        assertThat(m.firings(Map.of("warn_ratio", 1.0))[3]).isEqualTo(1.0);
        Model far = new Model(1, 1, List.of("f"), Map.of("f", new Model.Bounds(0, 1)),
                List.of(new Model.Rule("A", List.of(new Model.Antecedent("f", Model.Term.LOW, 0, 1e-9)), 0.9)));
        assertThat(far.infer(Map.of("f", 1.0)).score()).isZero();
    }

    @Test
    void featureSubsetValidation() throws IOException {
        Model m = seed();
        assertThat(m.missing(List.of("cpu_usage", "http_latency_p95_seconds", "error_ratio", "warn_ratio", "extra"))).isEmpty();
        assertThat(m.missing(List.of("cpu_usage"))).containsExactly("http_latency_p95_seconds", "error_ratio", "warn_ratio");
    }

    @Test
    void invalidModelsAreRejected() {
        assertThatThrownBy(() -> Model.parse("{not json".getBytes())).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> Model.parse("{\"feature_names\":[\"f\"],\"normalization\":{},\"rules\":[]}".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rulesAreRenderedLinguistically() throws IOException {
        assertThat(Model.render(seed().rules().get(1)))
                .isEqualTo("R2: IF cpu_usage is HIGH AND http_latency_p95_seconds is HIGH THEN 0.95");
    }

    @Test
    void seedModelScoresScenariosInIntendedRanges() throws IOException {
        Model m = seed();
        assertThat(m.infer(NORMAL).score()).isLessThan(0.3);
        assertThat(m.infer(ANOMALY).score()).isGreaterThan(0.8);
        assertThat(m.infer(NOISY).score()).isBetween(0.55, 0.75);
        assertThat(m.infer(NOISY).activeRules().get(0).id()).isEqualTo("R4");
    }

    @Test
    void falsePositiveFeedbackLowersNoisyScoreAndKeepsAnomalyHigh() throws IOException {
        Model m = seed();
        Model adapted = Adaptation.adapt(m, NOISY, Verdict.FALSE_POSITIVE, 0.5);
        assertThat(adapted.infer(NOISY).score()).isLessThan(m.infer(NOISY).score() - 0.1);
        assertThat(adapted.rules().get(3).consequent()).isLessThan(0.75);
        assertThat(adapted.infer(ANOMALY).score()).isGreaterThan(0.8);
    }

    @Test
    void missedFeedbackRaisesScore() throws IOException {
        Model m = seed();
        assertThat(Adaptation.adapt(m, NOISY, Verdict.MISSED, 0.5).infer(NOISY).score()).isGreaterThan(m.infer(NOISY).score());
    }
}
