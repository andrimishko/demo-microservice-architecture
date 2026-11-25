package ua.lab3.registry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Static config: which metrics a node type has, as PromQL templates with an {instance} placeholder. */
public enum NodeType {
    SPRING_BOOT("up", "cpu_usage", "memory_used_ratio", "http_latency_p95_seconds", "http_error_rate"),
    POSTGRES("up", "cpu_usage", "memory_used_ratio"),
    LINUX("up", "cpu_usage", "memory_used_ratio");

    private final Map<String, String> metrics = new LinkedHashMap<>();

    NodeType(String... names) {
        for (String n : names) metrics.put(n, n + "{instance=\"{instance}\"}");
    }

    public Map<String, String> metrics() {
        return metrics;
    }

    /** metric name -> PromQL for one concrete node. */
    public Map<String, String> queries(String nodeId) {
        Map<String, String> q = new LinkedHashMap<>();
        metrics.forEach((name, tpl) -> q.put(name, tpl.replace("{instance}", nodeId)));
        return q;
    }
}
