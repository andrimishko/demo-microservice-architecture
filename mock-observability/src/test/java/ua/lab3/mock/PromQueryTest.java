package ua.lab3.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PromQueryTest {
    @Test
    void parsesMetricWithInstance() {
        assertEquals(new PromQuery("cpu_usage", "node-1"), PromQuery.parse("cpu_usage{instance=\"node-1\"}"));
        assertEquals(new PromQuery("up", "node-1"), PromQuery.parse(" up{ instance = \"node-1\" } "));
    }

    @Test
    void parsesBareMetric() {
        assertNull(PromQuery.parse("up").instance());
        assertNull(PromQuery.parse("up{}").instance());
    }

    @Test
    void rejectsUnsupportedSyntax() {
        assertThrows(IllegalArgumentException.class, () -> PromQuery.parse("rate(up[5m])"));
        assertThrows(IllegalArgumentException.class, () -> PromQuery.parse("up{instance=~\"node.*\"}"));
    }
}
