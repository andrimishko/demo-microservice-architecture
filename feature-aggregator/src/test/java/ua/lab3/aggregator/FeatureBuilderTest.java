package ua.lab3.aggregator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.lab3.aggregator.FeatureBuilder.FeaturesBuilt;

class FeatureBuilderTest {
    @Test
    void vectorFollowsFixedOrderRegardlessOfQueryOrder() {
        PrometheusClient prom = mock(PrometheusClient.class);
        OpenSearchClient os = mock(OpenSearchClient.class);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        when(prom.max(eq("cpu{n}"), eq(start), eq(start.plusSeconds(9)))).thenReturn(0.7);
        when(prom.max(eq("up{n}"), any(), any())).thenReturn(1.0);
        when(os.logs(eq("n"), eq(start), any())).thenReturn(List.of(
                new LogRecord("INFO", "hit 1"), new LogRecord("ERROR", "fail 2")));

        Map<String, String> queries = new LinkedHashMap<>(); // deliberately not in METRIC_ORDER
        queries.put("cpu_usage", "cpu{n}");
        queries.put("up", "up{n}");
        FeaturesBuilt f = new FeatureBuilder(prom, os).build("n", queries, start, 10);

        assertEquals(List.of("up", "cpu_usage", "log_count", "warn_ratio", "error_ratio", "weighted_severity", "log_entropy"),
                f.featureNames());
        assertEquals(List.of(1.0, 0.7, 2.0, 0.0, 0.5, 2.5, 1.0), f.vector());
        assertEquals(1, f.schemaVersion());
        assertEquals(10, f.windowS());
    }
}
