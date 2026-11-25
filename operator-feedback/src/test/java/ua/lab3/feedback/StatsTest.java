package ua.lab3.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;
import ua.lab3.feedback.Dtos.Stats;

class StatsTest {
    private static FeedbackEntity fb(Verdict v, Cause c) {
        return new FeedbackEntity("id", "a", "node-1", v, c, null, "u", Instant.now());
    }

    @Test
    void ratesAndTopCauses() {
        Stats s = Stats.of(10, List.of(fb(Verdict.CONFIRMED, null), fb(Verdict.FALSE_POSITIVE, Cause.NOISY_LOGS),
                fb(Verdict.FALSE_POSITIVE, Cause.NOISY_LOGS), fb(Verdict.FALSE_POSITIVE, Cause.DEPLOY), fb(Verdict.MISSED, null)));
        assertEquals(10, s.alerts());
        assertEquals(1, s.confirmed());
        assertEquals(3, s.falsePositive());
        assertEquals(1, s.missed());
        assertEquals(0.75, s.fpRate());
        assertEquals(0.25, s.precision());
        assertEquals(List.of("NOISY_LOGS", "DEPLOY"), List.copyOf(s.topCauses().keySet()));
        assertEquals(2L, s.topCauses().get("NOISY_LOGS"));
    }

    @Test
    void noJudgedFeedbackGivesZeroRates() {
        Stats s = Stats.of(3, List.of(fb(Verdict.MISSED, null)));
        assertEquals(0, s.fpRate());
        assertEquals(0, s.precision());
    }
}
