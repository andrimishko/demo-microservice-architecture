package ua.lab3.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Anti-corruption layer for OpenSearch: hits become domain LogRecords. */
@Component
public class OpenSearchClient {
    private final RestClient http;

    public OpenSearchClient(@Value("${opensearch.url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Logs of one node with from <= @timestamp <= to (callers pass end - 1 ms for a half-open window). */
    public List<LogRecord> logs(String nodeId, Instant from, Instant to) {
        Map<String, Object> query = Map.of("size", 10000, "query", Map.of("bool", Map.of("filter", List.of(
                Map.of("term", Map.of("node_id", nodeId)),
                Map.of("range", Map.of("@timestamp", Map.of("gte", from.toString(), "lte", to.toString())))))));
        JsonNode body = http.post().uri("/logs/_search").body(query).retrieve().body(JsonNode.class);
        List<LogRecord> logs = new ArrayList<>();
        for (JsonNode hit : body.path("hits").path("hits"))
            logs.add(new LogRecord(hit.path("_source").path("severity").asText(), hit.path("_source").path("message").asText()));
        return logs;
    }
}
