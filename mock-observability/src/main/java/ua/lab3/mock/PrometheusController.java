package ua.lab3.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Imitates the Prometheus HTTP API (instant and range queries) in its real JSON shape. */
@RestController
public class PrometheusController {
    private static final int MAX_POINTS = 1000;

    private final MockData data;

    PrometheusController(MockData data) {
        this.data = data;
    }

    @GetMapping("/api/v1/query")
    Map<String, Object> query(@RequestParam String query) {
        PromQuery q = PromQuery.parse(query);
        double now = System.currentTimeMillis() / 1000.0;
        List<Object> result = new ArrayList<>();
        for (String node : selected(q))
            result.add(Map.of("metric", labels(q, node), "value", List.of(now, String.valueOf(data.value(q.metric(), node)))));
        return success("vector", result);
    }

    @GetMapping("/api/v1/query_range")
    Map<String, Object> queryRange(@RequestParam String query, @RequestParam String start,
                                   @RequestParam String end, @RequestParam String step) {
        PromQuery q = PromQuery.parse(query);
        double from = seconds(start), to = seconds(end), stepSec = seconds(step);
        if (stepSec <= 0 || (to - from) / stepSec > MAX_POINTS)
            throw new IllegalArgumentException("bad step or too many points");
        List<Object> result = new ArrayList<>();
        for (String node : selected(q)) {
            List<Object> values = new ArrayList<>();
            for (double t = from; t <= to; t += stepSec)
                values.add(List.of(t, String.valueOf(data.value(q.metric(), node))));
            result.add(Map.of("metric", labels(q, node), "values", values));
        }
        return success("matrix", result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badData(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", "error", "errorType", "bad_data", "error", e.getMessage()));
    }

    /** Nodes matched by the selector; unknown metric or instance gives an empty list. */
    private List<String> selected(PromQuery q) {
        if (!MockData.METRICS.contains(q.metric())) return List.of();
        if (q.instance() == null) return data.nodes();
        return data.known(q.instance()) ? List.of(q.instance()) : List.of();
    }

    private Map<String, String> labels(PromQuery q, String node) {
        return Map.of("__name__", q.metric(), "instance", node);
    }

    private static Map<String, Object> success(String type, List<Object> result) {
        return Map.of("status", "success", "data", Map.of("resultType", type, "result", result));
    }

    // shortcut: plain seconds ("60", "15s", "1700000000.5") only; no RFC 3339 or "1m" durations.
    private static double seconds(String s) {
        return Double.parseDouble(s.replaceAll("s$", ""));
    }
}
