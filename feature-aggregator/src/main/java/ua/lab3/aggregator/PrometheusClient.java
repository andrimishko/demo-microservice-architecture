package ua.lab3.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Anti-corruption layer for Prometheus: returns plain numbers, never JSON. */
@Component
public class PrometheusClient {
    private final RestClient http;

    public PrometheusClient(@Value("${prometheus.url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** MAX of all samples of the query over [from, to] (1 s step). Throws if Prometheus has no data. */
    public double max(String promql, Instant from, Instant to) {
        JsonNode body = http.get()
                .uri(u -> u.path("/api/v1/query_range").queryParam("query", "{q}").queryParam("start", from.getEpochSecond())
                        .queryParam("end", to.getEpochSecond()).queryParam("step", "1s").build(promql))
                .retrieve().body(JsonNode.class);
        double max = Double.NEGATIVE_INFINITY;
        for (JsonNode series : body.path("data").path("result"))
            for (JsonNode sample : series.path("values"))
                max = Math.max(max, sample.get(1).asDouble());
        if (max == Double.NEGATIVE_INFINITY) throw new IllegalStateException("no samples for " + promql);
        return max;
    }
}
