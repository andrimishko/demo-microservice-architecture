package ua.lab3.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ThresholdPolicyTest {
    @Test
    void classifiesByThresholds() {
        assertEquals(State.NORMAL, ThresholdPolicy.classify(0.49, 0.5, 0.8));
        assertEquals(State.WARNING, ThresholdPolicy.classify(0.5, 0.5, 0.8)); // boundary belongs to the higher state
        assertEquals(State.WARNING, ThresholdPolicy.classify(0.79, 0.5, 0.8));
        assertEquals(State.ANOMALY, ThresholdPolicy.classify(0.8, 0.5, 0.8));
    }
}
