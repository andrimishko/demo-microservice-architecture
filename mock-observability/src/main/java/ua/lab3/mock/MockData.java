package ua.lab3.mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** In-memory state (scenario per node) and the generators for metric values and log lines. */
@Component
public class MockData {
    public static final Set<String> METRICS = Set.of(
            "up", "cpu_usage", "memory_used_ratio", "http_latency_p95_seconds", "http_error_rate");

    public record LogLine(Instant timestamp, String nodeId, String severity, String message) {}

    private static final Map<String, String[]> TEMPLATES = Map.of(
            "INFO", new String[]{"Request to /api/orders completed in %d ms", "Cache hit for key user:%d"},
            "WARN", new String[]{"Slow response from payment gateway: %d ms", "Retrying request to /api/orders, attempt %d"},
            "ERROR", new String[]{"Failed to process order %d: connection timeout",
                    "Unhandled exception in /api/orders: NullPointerException (request %d)"});

    private final Map<String, Scenario> scenarios = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public MockData(@Value("${mock.nodes}") List<String> nodes) {
        nodes.forEach(n -> scenarios.put(n.trim(), Scenario.NORMAL));
    }

    public boolean known(String nodeId) {
        return scenarios.containsKey(nodeId);
    }

    public List<String> nodes() {
        return scenarios.keySet().stream().sorted().toList();
    }

    public Map<String, Scenario> scenarios() {
        return new TreeMap<>(scenarios);
    }

    public void setScenario(String nodeId, Scenario s) {
        scenarios.put(nodeId, s);
    }

    /** Scenario baseline with +-5% random noise; the metric must be one of METRICS. */
    public double value(String metric, String nodeId) {
        Scenario s = scenarios.get(nodeId);
        double base = switch (metric) {
            case "up" -> 1;
            case "cpu_usage" -> s.cpu;
            case "memory_used_ratio" -> s.mem;
            case "http_latency_p95_seconds" -> s.p95;
            case "http_error_rate" -> s.err;
            default -> throw new IllegalArgumentException(metric);
        };
        if (metric.equals("up")) return base;
        double v = base * (1 + (random.nextDouble() - 0.5) * 0.1);
        return metric.equals("http_latency_p95_seconds") ? v : Math.min(v, 1.0);
    }

    /** Log lines for the node, spread randomly over [from, to]; the count depends on the scenario, not on the range. */
    public List<LogLine> logs(String nodeId, Instant from, Instant to) {
        Scenario s = scenarios.get(nodeId);
        List<LogLine> lines = new ArrayList<>();
        for (int i = 0; i < s.logs; i++) {
            double r = random.nextDouble();
            String severity = r < s.errShare ? "ERROR" : r < s.errShare + s.warnShare ? "WARN" : "INFO";
            String[] templates = TEMPLATES.get(severity);
            String message = templates[random.nextInt(templates.length)].formatted(random.nextInt(1000));
            long span = Math.max(1, to.toEpochMilli() - from.toEpochMilli());
            lines.add(new LogLine(Instant.ofEpochMilli(from.toEpochMilli() + random.nextLong(span)), nodeId, severity, message));
        }
        lines.sort(Comparator.comparing(LogLine::timestamp));
        return lines;
    }
}
