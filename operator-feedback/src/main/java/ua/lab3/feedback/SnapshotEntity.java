package ua.lab3.feedback;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** What S4 classified: kept so feedback can carry the exact vector that was scored. */
@Entity
@Table(schema = "feedback", name = "analysis_snapshot")
public class SnapshotEntity {
    @Id
    private String analysisId;
    private String nodeId;
    private Instant windowStart;
    private String state;
    private double score;
    private int modelVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Double> vector;
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> featureNames;
    private Instant createdAt;

    protected SnapshotEntity() {}

    public SnapshotEntity(String analysisId, String nodeId, Instant windowStart, String state, double score,
                          int modelVersion, List<Double> vector, List<String> featureNames) {
        this.analysisId = analysisId;
        this.nodeId = nodeId;
        this.windowStart = windowStart;
        this.state = state;
        this.score = score;
        this.modelVersion = modelVersion;
        this.vector = vector;
        this.featureNames = featureNames;
        this.createdAt = Instant.now();
    }

    public String analysisId() { return analysisId; }
    public String nodeId() { return nodeId; }
    public Instant windowStart() { return windowStart; }
    public String state() { return state; }
    public List<Double> vector() { return vector; }
    public List<String> featureNames() { return featureNames; }
}
