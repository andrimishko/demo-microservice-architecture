package ua.lab3.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockDataTest {
    private final MockData data = new MockData(List.of("node-1", "node-2"));
    private final Instant now = Instant.now();

    private long count(String node, String severity) {
        return data.logs(node, now.minusSeconds(60), now).stream().filter(l -> l.severity().equals(severity)).count();
    }

    @Test
    void anomalyRaisesMetricsAndErrorLogs() {
        assertTrue(data.value("cpu_usage", "node-1") < 0.5);
        assertEquals(0, count("node-1", "ERROR"));
        data.setScenario("node-1", Scenario.ANOMALY);
        assertTrue(data.value("cpu_usage", "node-1") > 0.8);
        assertTrue(data.value("http_latency_p95_seconds", "node-1") > 1.0);
        assertTrue(count("node-1", "ERROR") > 0);
        assertTrue(data.value("cpu_usage", "node-2") < 0.5, "other nodes are unaffected");
    }

    @Test
    void noisyLogsKeepMetricsNormalAndHaveNoErrors() {
        data.setScenario("node-1", Scenario.NOISY_LOGS);
        assertTrue(data.value("cpu_usage", "node-1") < 0.5);
        assertEquals(0, count("node-1", "ERROR"));
        assertTrue(count("node-1", "WARN") > 5);
    }

    @Test
    void cpuSpikeBenignOnlyRaisesCpu() {
        data.setScenario("node-1", Scenario.CPU_SPIKE_BENIGN);
        assertTrue(data.value("cpu_usage", "node-1") > 0.8);
        assertTrue(data.value("http_error_rate", "node-1") < 0.1);
        assertFalse(data.logs("node-1", now.minusSeconds(60), now).stream().anyMatch(l -> l.severity().equals("ERROR")));
    }
}
