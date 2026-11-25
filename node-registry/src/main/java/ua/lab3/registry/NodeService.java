package ua.lab3.registry;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.EventPublisher;
import ua.lab3.common.Subjects;
import ua.lab3.registry.Dtos.CreateNode;
import ua.lab3.registry.Dtos.NodeChanged;
import ua.lab3.registry.Dtos.PatchNode;

/**
 * Not @Transactional on purpose: each repository call commits on return, and only then the event is published
 * (simplest "publish after commit"). shortcut: if publishing fails the row is already saved and the event is lost;
 * upgrade: transactional outbox.
 */
@Service
public class NodeService {
    private final NodeRepository repo;
    private final PrometheusClient prometheus;
    private final EventPublisher events;

    public NodeService(NodeRepository repo, PrometheusClient prometheus, EventPublisher events) {
        this.repo = repo;
        this.prometheus = prometheus;
        this.events = events;
    }

    public Node create(CreateNode req) {
        if (repo.existsById(req.nodeId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "node " + req.nodeId() + " already exists");
        if (!prometheus.hasMetrics(req.nodeId()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "no metrics found for " + req.nodeId());
        Node node = repo.save(new Node(req.nodeId(), req.name(), req.type(), req.thresholds()));
        publish("created", node);
        return node;
    }

    public List<Node> list(Boolean enabled) {
        return enabled == null ? repo.findAll() : repo.findByEnabled(enabled);
    }

    public Node get(String id) {
        return repo.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "node " + id + " not found"));
    }

    public Node patch(String id, PatchNode req) {
        Node node = get(id);
        node.update(req.thresholds(), req.enabled());
        node = repo.save(node);
        publish("updated", node);
        return node;
    }

    public void delete(String id) {
        Node node = get(id);
        repo.delete(node);
        publish("deleted", node);
    }

    public Map<String, String> queries(String id) {
        return get(id).type().queries(id);
    }

    private void publish(String op, Node node) {
        events.publish(Subjects.NODE_CHANGED, EventEnvelope.of(Subjects.NODE_CHANGED, NodeChanged.of(op, node)));
    }
}
