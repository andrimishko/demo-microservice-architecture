package ua.lab3.assessment;

import java.time.Instant;

/** One alert for a node. score and activeRules are kept to re-send the alert on escalation. */
public record Alert(
        String alertId,
        String nodeId,
        String analysisId,
        State state,
        double score,
        String activeRules,
        Priority priority,
        Instant sentAt,
        Status status) {

    public enum Priority { P1, P2, P3 }

    public enum Status { OPEN, ACKNOWLEDGED, ESCALATED, CLOSED }

    Alert with(Status s) {
        return new Alert(alertId, nodeId, analysisId, state, score, activeRules, priority, sentAt, s);
    }

    Alert escalated() {
        return new Alert(alertId, nodeId, analysisId, state, score, activeRules, Priority.P1, sentAt, Status.ESCALATED);
    }
}
