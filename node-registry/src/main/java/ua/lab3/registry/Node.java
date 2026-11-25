package ua.lab3.registry;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "registry", name = "node")
public class Node {
    @Id
    private String nodeId;
    private String name;
    @Enumerated(EnumType.STRING)
    private NodeType type;
    private double warning;
    private double anomaly;
    private boolean enabled;
    private Instant createdAt;

    protected Node() {}

    public Node(String nodeId, String name, NodeType type, Thresholds t) {
        this.nodeId = nodeId;
        this.name = name;
        this.type = type;
        this.warning = t.warning();
        this.anomaly = t.anomaly();
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    /** Null arguments leave the field unchanged. */
    public void update(Thresholds t, Boolean enabled) {
        if (t != null) {
            warning = t.warning();
            anomaly = t.anomaly();
        }
        if (enabled != null) this.enabled = enabled;
    }

    public String nodeId() { return nodeId; }
    public String name() { return name; }
    public NodeType type() { return type; }
    public Thresholds thresholds() { return new Thresholds(warning, anomaly); }
    public boolean enabled() { return enabled; }
    public Instant createdAt() { return createdAt; }
}
