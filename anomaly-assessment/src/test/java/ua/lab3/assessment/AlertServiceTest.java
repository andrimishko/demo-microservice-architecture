package ua.lab3.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ua.lab3.assessment.Alert.Priority;
import ua.lab3.assessment.Alert.Status;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;

class AlertServiceTest {
    /** A clock the test can move. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final AlertSender sender = mock(AlertSender.class);
    private final TestClock clock = new TestClock();
    private final AlertService service = new AlertService(sender, clock, Duration.ofSeconds(30));

    @Test
    void oneOpenAlertPerNodeAndClosedOnNormal() {
        service.onState("node-1", "a1", State.WARNING, 0.6, "R4");
        service.onState("node-1", "a2", State.WARNING, 0.6, "R4"); // already open: nothing new
        verify(sender, times(1)).send(any());
        assertEquals(Priority.P3, service.all().get(0).priority());

        service.onState("node-1", "a3", State.NORMAL, 0.05, "R1");
        assertEquals(Status.CLOSED, service.all().get(0).status());

        service.onState("node-1", "a4", State.ANOMALY, 0.9, "R3"); // a new alert after the first one was closed
        verify(sender, times(2)).send(any());
        assertEquals(2, service.all().size());
        assertEquals(Priority.P2, service.all().get(1).priority());
    }

    @Test
    void worseningWarningIsReplacedByAnomalyAlert() {
        service.onState("node-1", "a1", State.WARNING, 0.6, "R4");
        service.onState("node-1", "a2", State.ANOMALY, 0.9, "R3");
        assertEquals(Status.CLOSED, service.all().get(0).status());
        assertEquals(Status.OPEN, service.all().get(1).status());
    }

    @Test
    void escalationDecisionDependsOnStateStatusAndAge() {
        Instant t0 = clock.instant();
        Alert anomaly = new Alert("id", "node-1", "a1", State.ANOMALY, 0.9, "R3", Priority.P2, t0, Status.OPEN);
        Duration after = Duration.ofSeconds(30);
        assertFalse(AlertService.escalationDue(anomaly, t0.plusSeconds(29), after));
        assertTrue(AlertService.escalationDue(anomaly, t0.plusSeconds(30), after));
        assertFalse(AlertService.escalationDue(anomaly.with(Status.ACKNOWLEDGED), t0.plusSeconds(60), after));
        assertFalse(AlertService.escalationDue(anomaly.escalated(), t0.plusSeconds(60), after)); // only once
        Alert warning = new Alert("id", "node-1", "a1", State.WARNING, 0.6, "R4", Priority.P3, t0, Status.OPEN);
        assertFalse(AlertService.escalationDue(warning, t0.plusSeconds(60), after));
    }

    @Test
    void scheduledEscalationResendsOnceAsP1() {
        service.onState("node-1", "a1", State.ANOMALY, 0.9, "R3");
        service.escalate();
        verify(sender, times(1)).send(any()); // too early

        clock.now = clock.now.plusSeconds(31);
        service.escalate();
        service.escalate();
        ArgumentCaptor<Alert> sent = ArgumentCaptor.forClass(Alert.class);
        verify(sender, times(2)).send(sent.capture());
        assertEquals(Priority.P1, sent.getValue().priority());
        assertEquals(Status.ESCALATED, service.all().get(0).status());
    }

    @Test
    void confirmedAcknowledgesAndStopsEscalation() {
        service.onState("node-1", "a1", State.ANOMALY, 0.9, "R3");
        service.onFeedback("node-1", "a1", Verdict.CONFIRMED, "UNKNOWN");
        clock.now = clock.now.plusSeconds(60);
        service.escalate();
        assertEquals(Status.ACKNOWLEDGED, service.all().get(0).status());
        verify(sender, times(1)).send(any());
    }

    @Test
    void falsePositiveClosesAlertButOtherAnalysisIsIgnored() {
        service.onState("node-1", "a1", State.WARNING, 0.6, "R4");
        service.onFeedback("node-1", "other", Verdict.FALSE_POSITIVE, "NOISY_LOGS");
        assertEquals(Status.OPEN, service.all().get(0).status());
        service.onFeedback("node-1", "a1", Verdict.FALSE_POSITIVE, "NOISY_LOGS");
        assertEquals(Status.CLOSED, service.all().get(0).status());
    }

    @Test
    void failedSendLeavesNoAlertSoTheNextWindowRetries() {
        org.mockito.Mockito.doThrow(new IllegalStateException("down")).doNothing().when(sender).send(any());
        service.onState("node-1", "a1", State.WARNING, 0.6, "R4");
        assertTrue(service.all().isEmpty());
        service.onState("node-1", "a2", State.WARNING, 0.6, "R4");
        assertEquals(1, service.all().size());
    }
}
