package ua.lab3.aggregator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Log part of the feature vector: severity shares and template entropy (simplified Drain). */
public final class LogFeatures {
    private LogFeatures() {}

    private static final Map<String, Integer> WEIGHT = Map.of("INFO", 1, "WARN", 2, "ERROR", 4);

    public static String template(String message) {
        return message.replaceAll("\\b[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}\\b|\\d+", "<*>");
    }

    public static double entropy(List<LogRecord> logs) {
        Map<String, Integer> counts = new HashMap<>();
        for (LogRecord l : logs) counts.merge(template(l.message()), 1, Integer::sum);
        double h = 0;
        for (int c : counts.values()) {
            double p = (double) c / logs.size();
            h -= p * Math.log(p) / Math.log(2);
        }
        return h;
    }

    public static Map<String, Double> of(List<LogRecord> logs) {
        int n = logs.size();
        double warn = logs.stream().filter(l -> l.severity().equals("WARN")).count();
        double error = logs.stream().filter(l -> l.severity().equals("ERROR")).count();
        double weights = logs.stream().mapToInt(l -> WEIGHT.getOrDefault(l.severity(), 1)).sum();
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("log_count", (double) n);
        f.put("warn_ratio", n == 0 ? 0 : warn / n);
        f.put("error_ratio", n == 0 ? 0 : error / n);
        f.put("weighted_severity", n == 0 ? 0 : weights / n);
        f.put("log_entropy", n == 0 ? 0 : entropy(logs));
        return f;
    }
}
