package ua.lab3.assessment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ua.lab3.assessment.Alert.Priority;
import ua.lab3.assessment.Alert.Status;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;

/**
 * Keeps at most one open alert per node. Everything is synchronized: the score consumer, the feedback consumer and
 * the scheduler call in from different threads.
 * shortcut: alerts live in memory (lost on restart, history never trimmed); upgrade: a table in Postgres.
 */
@Service
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertSender sender;
    private final Clock clock;
    private final Duration escalationAfter;
    private final Map<String, Alert> alerts = new LinkedHashMap<>();     // alertId -> alert, all history
    private final Map<String, String> openByNode = new HashMap<>();      // nodeId -> alertId of its open alert
    private final Map<String, Integer> falsePositives = new HashMap<>(); // "node/cause" -> count

    AlertService(AlertSender sender, Clock clock, @Value("${assessment.escalation-after}") Duration escalationAfter) {
        this.sender = sender;
        this.clock = clock;
        this.escalationAfter = escalationAfter;
    }

    public synchronized List<Alert> all() {
        return new ArrayList<>(alerts.values());
    }

    /** Called for every classified window. NORMAL closes the node's alert; WARNING/ANOMALY opens one if there is none. */
    public synchronized void onState(String nodeId, String analysisId, State state, double score, String rules) {
        Alert open = openAlert(nodeId);
        if (state == State.NORMAL) {
            if (open != null) close(open);
            return;
        }
        if (open != null && !(open.state() == State.WARNING && state == State.ANOMALY)) return;
        if (open != null) close(open); // the node got worse: replace the P3 alert with a P2 one
        Alert a = new Alert(UUID.randomUUID().toString(), nodeId, analysisId, state, score, rules,
                state == State.ANOMALY ? Priority.P2 : Priority.P3, clock.instant(), Status.OPEN);
        try {
            sender.send(a);
        } catch (Exception e) {
            log.warn("alert for {} not sent, the next window retries: {}", nodeId, e.toString());
            return;
        }
        alerts.put(a.alertId(), a);
        openByNode.put(nodeId, a.alertId());
        log.info("alert {} opened on {}: {} {}", a.priority(), nodeId, state, analysisId);
    }

    /**
     * Operator verdict on an analysis. Only the analysis that opened the node's current alert is acted on.
     * shortcut: false positives are only counted; changing the node's thresholds from them is out of scope.
     */
    public synchronized void onFeedback(String nodeId, String analysisId, Verdict verdict, String cause) {
        if (verdict == Verdict.MISSED) {
            log.info("missed anomaly reported on {} ({})", nodeId, analysisId);
            return;
        }
        Alert open = openAlert(nodeId);
        if (open == null || !open.analysisId().equals(analysisId)) return;
        if (verdict == Verdict.CONFIRMED) {
            alerts.put(open.alertId(), open.with(Status.ACKNOWLEDGED));
            log.info("alert on {} acknowledged", nodeId);
        } else {
            close(open);
            int total = falsePositives.merge(nodeId + "/" + cause, 1, Integer::sum);
            log.info("false positive on {} cause={} (total {})", nodeId, cause, total);
        }
    }

    @Scheduled(fixedDelay = 5000)
    synchronized void escalate() {
        for (Alert a : List.copyOf(alerts.values())) {
            if (!escalationDue(a, clock.instant(), escalationAfter)) continue;
            Alert p1 = a.escalated();
            try {
                sender.send(p1);
            } catch (Exception e) {
                log.warn("escalation of {} not sent, retry in 5 s: {}", a.nodeId(), e.toString());
                continue;
            }
            alerts.put(a.alertId(), p1);
            log.info("alert on {} escalated to P1", a.nodeId());
        }
    }

    /** An ANOMALY alert nobody has reacted to for `after` is due for escalation. */
    static boolean escalationDue(Alert a, Instant now, Duration after) {
        return a.state() == State.ANOMALY && a.status() == Status.OPEN && !now.isBefore(a.sentAt().plus(after));
    }

    private Alert openAlert(String nodeId) {
        String id = openByNode.get(nodeId);
        return id == null ? null : alerts.get(id);
    }

    private void close(Alert a) {
        alerts.put(a.alertId(), a.with(Status.CLOSED));
        openByNode.remove(a.nodeId());
        log.info("alert on {} closed", a.nodeId());
    }
}
