package ua.lab3.mock;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Imitates OpenSearch `POST /{index}/_search` for log lines. */
@RestController
public class OpenSearchController {
    private final MockData data;

    OpenSearchController(MockData data) {
        this.data = data;
    }

    // shortcut: finds the first `node_id` and `@timestamp` {gte,lte} anywhere in the body (ISO-8601 only, no "now-1m"
    // math), ignores the index name, size and every other clause. Upgrade: interpret the query DSL properly.
    @PostMapping("/{index}/_search")
    Map<String, Object> search(@PathVariable String index, @RequestBody JsonNode body) {
        JsonNode node = body.findValue("node_id");
        String nodeId = node == null ? null : node.isObject() ? node.path("value").asText() : node.asText();
        JsonNode range = body.findValue("@timestamp");
        Instant to = instant(range, "lte", Instant.now());
        Instant from = instant(range, "gte", to.minusSeconds(60));

        List<Map<String, Object>> hits = nodeId == null || !data.known(nodeId) ? List.of()
                : data.logs(nodeId, from, to).stream().map(l -> Map.<String, Object>of("_source", Map.of(
                        "@timestamp", l.timestamp().toString(), "node_id", l.nodeId(),
                        "severity", l.severity(), "message", l.message()))).toList();
        return Map.of("hits", Map.of("total", Map.of("value", hits.size(), "relation", "eq"), "hits", hits));
    }

    private static Instant instant(JsonNode range, String field, Instant fallback) {
        try {
            return range == null || !range.has(field) ? fallback : Instant.parse(range.get(field).asText());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
