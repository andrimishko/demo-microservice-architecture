package ua.lab3.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.Subjects;

/**
 * Thresholds of the enabled nodes: loaded from node-registry at startup, then kept current from node.changed
 * (durable push consumer "anomaly-assessment-nodes", DeliverPolicy.All, same idea as S2's NodeCache).
 * We subscribe first and load second, so no change falls in the gap; replayed older events are overwritten by newer ones.
 */
@Component
public class ThresholdsCache {
    private static final Logger log = LoggerFactory.getLogger(ThresholdsCache.class);

    public record Thresholds(double warning, double anomaly) {}

    private final Map<String, Thresholds> nodes = new ConcurrentHashMap<>();
    private final CountDownLatch loaded = new CountDownLatch(1);
    private final RestClient registry;
    private final Connection connection;
    private final JetStream js;

    ThresholdsCache(@Value("${registry.url}") String registryUrl, Connection connection, JetStream js) {
        this.registry = RestClient.builder().baseUrl(registryUrl).build();
        this.connection = connection;
        this.js = js;
    }

    public Optional<Thresholds> get(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /** Blocks until the first load from the registry has finished. */
    void awaitLoaded() throws InterruptedException {
        loaded.await();
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() throws Exception {
        js.subscribe(Subjects.NODE_CHANGED, connection.createDispatcher(), this::onMessage, false,
                PushSubscribeOptions.builder().durable("anomaly-assessment-nodes")
                        .configuration(ConsumerConfiguration.builder().deliverPolicy(DeliverPolicy.All)
                                .ackWait(Duration.ofSeconds(30)).build()).build());
        while (true) {
            try {
                JsonNode list = registry.get().uri("/v1/nodes").retrieve().body(JsonNode.class);
                for (JsonNode n : list) apply(n.path("node_id").asText(), n.path("thresholds"), n.path("enabled").asBoolean());
                log.info("loaded thresholds of {} node(s): {}", nodes.size(), nodes);
                loaded.countDown();
                return;
            } catch (Exception e) {
                log.warn("node-registry not ready ({}), retrying in 3 s", e.toString());
                Thread.sleep(3000);
            }
        }
    }

    private void onMessage(Message m) {
        try {
            JsonNode p = EventEnvelope.MAPPER.readTree(m.getData()).path("payload");
            boolean deleted = p.path("op").asText().equals("deleted");
            apply(p.path("node_id").asText(), p.path("thresholds"), p.path("enabled").asBoolean() && !deleted);
            m.ack();
        } catch (Exception e) {
            log.warn("node.changed failed, redelivery in 5 s: {}", e.toString());
            m.nakWithDelay(Duration.ofSeconds(5));
        }
    }

    private void apply(String id, JsonNode t, boolean enabled) {
        if (enabled) nodes.put(id, new Thresholds(t.path("warning").asDouble(), t.path("anomaly").asDouble()));
        else nodes.remove(id);
    }
}
