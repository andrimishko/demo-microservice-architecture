package ua.lab3.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Request/response bodies of the REST API and the node.changed event payload. */
public final class Dtos {
    private Dtos() {}

    public record CreateNode(
            @JsonProperty("node_id") @NotBlank String nodeId,
            @NotBlank String name,
            @NotNull NodeType type,
            @NotNull @Valid Thresholds thresholds) {}

    public record PatchNode(@Valid Thresholds thresholds, Boolean enabled) {}

    public record NodeView(
            @JsonProperty("node_id") String nodeId,
            String name,
            NodeType type,
            Thresholds thresholds,
            boolean enabled,
            @JsonProperty("created_at") Instant createdAt) {
        static NodeView of(Node n) {
            return new NodeView(n.nodeId(), n.name(), n.type(), n.thresholds(), n.enabled(), n.createdAt());
        }
    }

    public record NodeChanged(
            String op,
            @JsonProperty("node_id") String nodeId,
            NodeType type,
            Thresholds thresholds,
            boolean enabled) {
        static NodeChanged of(String op, Node n) {
            return new NodeChanged(op, n.nodeId(), n.type(), n.thresholds(), n.enabled());
        }
    }
}
