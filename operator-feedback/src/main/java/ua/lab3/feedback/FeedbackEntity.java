package ua.lab3.feedback;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import ua.lab3.common.events.FeedbackSubmitted.Verdict;

@Entity
@Table(schema = "feedback", name = "feedback")
public class FeedbackEntity {
    @Id
    private String feedbackId;
    private String analysisId; // unique in the DB: one verdict per analysis
    private String nodeId;
    @Enumerated(EnumType.STRING)
    private Verdict verdict;
    @Enumerated(EnumType.STRING)
    private Cause cause;
    private String comment;
    private String author;
    private Instant createdAt;

    protected FeedbackEntity() {}

    public FeedbackEntity(String feedbackId, String analysisId, String nodeId, Verdict verdict, Cause cause,
                          String comment, String author, Instant createdAt) {
        this.feedbackId = feedbackId;
        this.analysisId = analysisId;
        this.nodeId = nodeId;
        this.verdict = verdict;
        this.cause = cause;
        this.comment = comment;
        this.author = author;
        this.createdAt = createdAt;
    }

    public String feedbackId() { return feedbackId; }
    public String analysisId() { return analysisId; }
    public String nodeId() { return nodeId; }
    public Verdict verdict() { return verdict; }
    public Cause cause() { return cause; }
    public String comment() { return comment; }
    public String author() { return author; }
    public Instant createdAt() { return createdAt; }
}
