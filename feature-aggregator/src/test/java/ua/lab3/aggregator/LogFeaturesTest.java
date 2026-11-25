package ua.lab3.aggregator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LogFeaturesTest {
    @Test
    void templateReplacesNumbersAndUuids() {
        assertEquals("Failed to process order <*>: timeout after <*> ms",
                LogFeatures.template("Failed to process order 42: timeout after 1500 ms"));
        assertEquals("user <*> ok", LogFeatures.template("user 123e4567-e89b-12d3-a456-426614174000 ok"));
    }

    @Test
    void entropyOfTemplateDistribution() {
        LogRecord a1 = new LogRecord("INFO", "cache hit 1"), a2 = new LogRecord("INFO", "cache hit 2");
        LogRecord b = new LogRecord("WARN", "slow 9 ms");
        assertEquals(0, LogFeatures.entropy(List.of(a1, a2)), 1e-9);           // one template
        assertEquals(1, LogFeatures.entropy(List.of(a1, b)), 1e-9);            // 50/50 -> 1 bit
        assertEquals(0, LogFeatures.entropy(List.of()), 1e-9);
    }

    @Test
    void severityFeatures() {
        var f = LogFeatures.of(List.of(new LogRecord("INFO", "a"), new LogRecord("WARN", "b"),
                new LogRecord("ERROR", "c"), new LogRecord("ERROR", "c")));
        assertEquals(4, f.get("log_count"));
        assertEquals(0.25, f.get("warn_ratio"));
        assertEquals(0.5, f.get("error_ratio"));
        assertEquals((1 + 2 + 4 + 4) / 4.0, f.get("weighted_severity"));
        assertEquals(List.of("log_count", "warn_ratio", "error_ratio", "weighted_severity", "log_entropy"),
                List.copyOf(f.keySet()));
    }

    @Test
    void noLogsGivesZeros() {
        assertEquals(0.0, LogFeatures.of(List.of()).get("weighted_severity"));
    }
}
