package ua.lab3.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ua.lab3.common.EventEnvelope;

/** Zero-order TSK fuzzy model: Gaussian membership functions, product AND, weighted-average defuzzification. */
public record Model(
        int version,
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("feature_names") List<String> featureNames,
        Map<String, Bounds> normalization,
        List<Rule> rules) {

    public enum Term { LOW, MEDIUM, HIGH }

    public record Bounds(double min, double max) {}

    /** center and width are in the NORMALIZED [0,1] space; the term is only a human-readable label. */
    public record Antecedent(String feature, Term term, double center, double width) {}

    public record Rule(String id, List<Antecedent> antecedent, double consequent) {}

    public record ActiveRule(String id, double strength) {}

    public record Result(double score, @JsonProperty("active_rules") List<ActiveRule> activeRules) {}

    static final double ACTIVE_THRESHOLD = 0.1;

    public static Model parse(byte[] json) throws IOException {
        Model m = EventEnvelope.MAPPER.readValue(json, Model.class);
        m.validate();
        return m;
    }

    public Model withVersion(int v) {
        return new Model(v, schemaVersion, featureNames, normalization, rules);
    }

    public Model withRules(List<Rule> r) {
        return new Model(version, schemaVersion, featureNames, normalization, r);
    }

    /** Throws IllegalArgumentException with a human-readable reason if the artifact is malformed. */
    void validate() {
        require(featureNames != null && !featureNames.isEmpty(), "feature_names is empty");
        require(normalization != null && rules != null && !rules.isEmpty(), "normalization or rules missing");
        for (String f : featureNames) {
            Bounds b = normalization.get(f);
            require(b != null && b.max() > b.min(), "normalization for " + f + " missing or min >= max");
        }
        for (Rule r : rules) {
            require(r.id() != null && r.antecedent() != null && !r.antecedent().isEmpty(), "rule without id or antecedent");
            require(r.consequent() >= 0 && r.consequent() <= 1, r.id() + ": consequent outside [0,1]");
            for (Antecedent a : r.antecedent()) {
                require(featureNames.contains(a.feature()), r.id() + ": feature " + a.feature() + " not in feature_names");
                require(a.term() != null && a.width() > 0, r.id() + ": bad term or width");
            }
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    /** Names this model needs that the given vector lacks (empty = vector is usable). */
    public List<String> missing(List<String> available) {
        return featureNames.stream().filter(f -> !available.contains(f)).toList();
    }

    public static Map<String, Double> asMap(List<String> names, List<Double> vector) {
        Map<String, Double> m = new java.util.HashMap<>();
        for (int i = 0; i < names.size(); i++) m.put(names.get(i), vector.get(i));
        return m;
    }

    static double gaussian(double x, double center, double width) {
        double z = (x - center) / width;
        return Math.exp(-0.5 * z * z);
    }

    private double normalize(String feature, double x) {
        Bounds b = normalization.get(feature);
        return Math.max(0, Math.min(1, (x - b.min()) / (b.max() - b.min())));
    }

    /** Raw firing strength per rule (same order as rules): product of the antecedent memberships. */
    double[] firings(Map<String, Double> x) {
        double[] w = new double[rules.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = 1;
            for (Antecedent a : rules.get(i).antecedent()) {
                Double v = x.get(a.feature());
                if (v != null) w[i] *= gaussian(normalize(a.feature(), v), a.center(), a.width()); // missing feature contributes 1
            }
        }
        return w;
    }

    /** Normalized firing strengths w̄ = w / Σw (all zeros when nothing fires). */
    double[] normalizedFirings(Map<String, Double> x) {
        double[] w = firings(x);
        double sum = 0;
        for (double v : w) sum += v;
        if (sum == 0) return w;
        for (int i = 0; i < w.length; i++) w[i] /= sum;
        return w;
    }

    public Result infer(Map<String, Double> x) {
        double[] wbar = normalizedFirings(x);
        double score = 0;
        List<ActiveRule> active = new ArrayList<>();
        for (int i = 0; i < wbar.length; i++) {
            score += wbar[i] * rules.get(i).consequent(); // Σ w·c / Σ w
            if (wbar[i] >= ACTIVE_THRESHOLD) active.add(new ActiveRule(rules.get(i).id(), Math.round(wbar[i] * 1000) / 1000.0));
        }
        active.sort((a, b) -> Double.compare(b.strength(), a.strength()));
        return new Result(Math.round(score * 1000) / 1000.0, active);
    }

    /** e.g. "R2: IF cpu_usage is HIGH AND http_latency_p95_seconds is HIGH THEN 0.95" */
    public static String render(Rule r) {
        List<String> parts = r.antecedent().stream().map(a -> a.feature() + " is " + a.term()).toList();
        return r.id() + ": IF " + String.join(" AND ", parts) + " THEN " + r.consequent();
    }
}
