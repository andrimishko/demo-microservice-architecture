package ua.lab3.mock;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Imitates the OpenSearch index `analysis-results`: keeps documents in memory. */
@RestController
public class AnalysisResultsController {
    private static final Logger log = LoggerFactory.getLogger(AnalysisResultsController.class);
    private final List<JsonNode> docs = new CopyOnWriteArrayList<>();

    @PostMapping("/analysis-results/_doc")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> index(@RequestBody JsonNode doc) {
        log.info("analysis result stored: {}", doc);
        docs.add(doc);
        return Map.of("result", "created", "_id", UUID.randomUUID().toString());
    }

    // shortcut: ignores the query and returns every stored document. Upgrade: interpret the query DSL.
    @PostMapping("/analysis-results/_search")
    Map<String, Object> search(@RequestBody(required = false) JsonNode ignored) {
        List<Map<String, Object>> hits = docs.stream().map(d -> Map.<String, Object>of("_source", d)).toList();
        return Map.of("hits", Map.of("total", Map.of("value", hits.size(), "relation", "eq"), "hits", hits));
    }
}
