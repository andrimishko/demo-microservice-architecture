package ua.lab3.feedback;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.EventPublisher;
import ua.lab3.common.Subjects;
import ua.lab3.common.events.FeedbackSubmitted;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;
import ua.lab3.feedback.Dtos.MissedRequest;
import ua.lab3.feedback.Dtos.MissedResult;
import ua.lab3.feedback.Dtos.Stats;

/**
 * Not @Transactional on purpose (same as S1): each repository call commits on return, then the event is published.
 * shortcut: if publishing fails the feedback row is already saved and the event is lost; upgrade: transactional outbox.
 */
@Service
public class FeedbackService {
    private final FeedbackRepository feedback;
    private final SnapshotRepository snapshots;
    private final SuggestionRepository suggestions;
    private final EventPublisher events;

    public FeedbackService(FeedbackRepository feedback, SnapshotRepository snapshots, SuggestionRepository suggestions,
                           EventPublisher events) {
        this.feedback = feedback;
        this.snapshots = snapshots;
        this.suggestions = suggestions;
        this.events = events;
    }

    public FeedbackEntity submit(FeedbackRequest req, String author) {
        if (req.verdict() == Verdict.MISSED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "use POST /v1/feedback/missed for MISSED");
        if (req.verdict() == Verdict.FALSE_POSITIVE && req.cause() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cause is required for FALSE_POSITIVE");
        SnapshotEntity snapshot = snapshots.findById(req.analysisId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "analysis " + req.analysisId() + " not found"));
        if (feedback.existsByAnalysisId(req.analysisId())) throw conflict(req.analysisId());
        return save(snapshot, req.verdict(), req.cause(), req.comment(), author);
    }

    public MissedResult markMissed(MissedRequest req, String author) {
        if (!req.from().isBefore(req.to()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        List<SnapshotEntity> found = snapshots.findWithoutFeedback(req.nodeId(), req.from(), req.to());
        if (found.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no analyses without feedback for " + req.nodeId() + " in range");
        List<String> ids = found.stream()
                .map(s -> save(s, Verdict.MISSED, null, req.comment(), author).feedbackId()).toList();
        return new MissedResult(ids, ids.size());
    }

    // shortcut: node and verdict are filtered in memory after loading the period; upgrade: a JPA Specification.
    public List<FeedbackEntity> list(String nodeId, Instant from, Instant to, Verdict verdict) {
        return feedback.findByCreatedAtBetween(from == null ? Instant.EPOCH : from, to == null ? Instant.now() : to)
                .stream().filter(f -> nodeId == null || f.nodeId().equals(nodeId))
                .filter(f -> verdict == null || f.verdict() == verdict).toList();
    }

    public Stats stats(String nodeId, String period) {
        Duration d;
        try {
            d = Duration.parse(period);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must be an ISO-8601 duration, e.g. PT1H");
        }
        Instant since = Instant.now().minus(d);
        return Stats.of(snapshots.countByNodeIdAndStateNotAndWindowStartAfter(nodeId, "NORMAL", since),
                feedback.findByNodeIdAndCreatedAtAfter(nodeId, since));
    }

    public SuggestionEntity suggest(String text, String author) {
        return suggestions.save(new SuggestionEntity(UUID.randomUUID().toString(), text, author, Instant.now()));
    }

    private FeedbackEntity save(SnapshotEntity s, Verdict verdict, Cause cause, String comment, String author) {
        FeedbackEntity f = new FeedbackEntity(UUID.randomUUID().toString(), s.analysisId(), s.nodeId(), verdict, cause,
                comment, author, Instant.now());
        try {
            f = feedback.save(f);
        } catch (DataIntegrityViolationException e) { // the unique constraint is the backstop for two concurrent requests
            throw conflict(s.analysisId());
        }
        events.publish(Subjects.FEEDBACK_SUBMITTED, EventEnvelope.of(Subjects.FEEDBACK_SUBMITTED,
                new FeedbackSubmitted(f.feedbackId(), f.analysisId(), f.nodeId(), verdict,
                        cause == null ? null : cause.name(), s.vector(), s.featureNames())), f.feedbackId());
        return f;
    }

    private static ResponseStatusException conflict(String analysisId) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "feedback for analysis " + analysisId + " already exists");
    }
}
