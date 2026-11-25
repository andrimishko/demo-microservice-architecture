package ua.lab3.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.EventPublisher;
import ua.lab3.common.Subjects;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;
import ua.lab3.feedback.Dtos.MissedRequest;

class FeedbackServiceTest {
    private final FeedbackRepository feedback = mock(FeedbackRepository.class);
    private final SnapshotRepository snapshots = mock(SnapshotRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final FeedbackService service = new FeedbackService(feedback, snapshots, mock(SuggestionRepository.class), events);
    private final SnapshotEntity snapshot = new SnapshotEntity("a1", "node-1", Instant.parse("2026-01-01T00:00:00Z"),
            "WARNING", 0.6, 1, List.of(0.1, 0.9), List.of("cpu_usage", "warn_ratio"));

    private HttpStatus statusOf(Runnable r) {
        return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode();
    }

    @Test
    void falsePositiveWithoutCauseIs400() {
        assertEquals(HttpStatus.BAD_REQUEST,
                statusOf(() -> service.submit(new FeedbackRequest("a1", Verdict.FALSE_POSITIVE, null, null), "u")));
    }

    @Test
    void missedVerdictIs400() {
        assertEquals(HttpStatus.BAD_REQUEST,
                statusOf(() -> service.submit(new FeedbackRequest("a1", Verdict.MISSED, null, null), "u")));
    }

    @Test
    void unknownAnalysisIs404() {
        when(snapshots.findById("a1")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,
                statusOf(() -> service.submit(new FeedbackRequest("a1", Verdict.CONFIRMED, null, null), "u")));
    }

    @Test
    void secondFeedbackIs409() {
        when(snapshots.findById("a1")).thenReturn(Optional.of(snapshot));
        when(feedback.existsByAnalysisId("a1")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT,
                statusOf(() -> service.submit(new FeedbackRequest("a1", Verdict.CONFIRMED, null, null), "u")));
        verify(events, never()).publish(any(), any(), any());
    }

    @Test
    void uniqueConstraintRaceIs409() {
        when(snapshots.findById("a1")).thenReturn(Optional.of(snapshot));
        when(feedback.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertEquals(HttpStatus.CONFLICT,
                statusOf(() -> service.submit(new FeedbackRequest("a1", Verdict.CONFIRMED, null, null), "u")));
        verify(events, never()).publish(any(), any(), any());
    }

    @Test
    void successSavesAndPublishesEventWithSnapshotVector() {
        when(snapshots.findById("a1")).thenReturn(Optional.of(snapshot));
        when(feedback.save(any())).thenAnswer(i -> i.getArgument(0));

        FeedbackEntity f = service.submit(new FeedbackRequest("a1", Verdict.FALSE_POSITIVE, Cause.NOISY_LOGS, "x"), "ann");

        ArgumentCaptor<EventEnvelope> event = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events).publish(eq(Subjects.FEEDBACK_SUBMITTED), event.capture(), eq(f.feedbackId()));
        var p = event.getValue().payload();
        assertEquals("a1", p.get("analysis_id").asText());
        assertEquals("FALSE_POSITIVE", p.get("verdict").asText());
        assertEquals("NOISY_LOGS", p.get("cause").asText());
        assertEquals(0.9, p.get("vector").get(1).asDouble());
        assertEquals("warn_ratio", p.get("feature_names").get(1).asText());
        assertEquals("ann", f.author());
    }

    @Test
    void missedRangeValidationAndOneEventPerSnapshot() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z"), to = from.plusSeconds(60);
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.markMissed(new MissedRequest("node-1", to, from, null), "u")));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.markMissed(new MissedRequest("node-1", from, to, null), "u")));

        when(snapshots.findWithoutFeedback("node-1", from, to)).thenReturn(List.of(snapshot, snapshot));
        when(feedback.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.markMissed(new MissedRequest("node-1", from, to, null), "u");
        assertEquals(2, result.markedAnalyses());
        verify(events, org.mockito.Mockito.times(2)).publish(eq(Subjects.FEEDBACK_SUBMITTED), any(), any());
    }
}
