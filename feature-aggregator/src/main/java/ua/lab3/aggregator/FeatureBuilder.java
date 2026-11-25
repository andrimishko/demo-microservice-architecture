package ua.lab3.aggregator;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds one feature vector for one node and one window. */
@Component
public class FeatureBuilder {
    public static final int SCHEMA_VERSION = 1;
    static final List<String> METRIC_ORDER = List.of(
            "up", "cpu_usage", "memory_used_ratio", "http_latency_p95_seconds", "http_error_rate");

    public record FeaturesBuilt(
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("window_start") Instant windowStart,
            @JsonProperty("window_s") int windowS,
            List<Double> vector,
            @JsonProperty("feature_names") List<String> featureNames,
            @JsonProperty("schema_version") int schemaVersion) {}

    private final PrometheusClient prometheus;
    private final OpenSearchClient opensearch;

    public FeatureBuilder(PrometheusClient prometheus, OpenSearchClient opensearch) {
        this.prometheus = prometheus;
        this.opensearch = opensearch;
    }

    public FeaturesBuilt build(String nodeId, Map<String, String> queries, Instant start, int windowS) {
        Instant end = start.plusSeconds(windowS);
        List<String> names = new ArrayList<>();
        List<Double> vector = new ArrayList<>();
        for (String metric : METRIC_ORDER) {
            if (!queries.containsKey(metric)) continue;
            names.add(metric);
            vector.add(prometheus.max(queries.get(metric), start, end.minusSeconds(1)));
        }
        List<LogRecord> logs = opensearch.logs(nodeId, start, end.minus(1, ChronoUnit.MILLIS));
        LogFeatures.of(logs).forEach((name, value) -> {
            names.add(name);
            vector.add(value);
        });
        return new FeaturesBuilt(nodeId, start, windowS, vector, names, SCHEMA_VERSION);
    }
}
