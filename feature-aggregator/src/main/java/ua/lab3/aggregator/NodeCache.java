package ua.lab3.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.Subjects;

/**
 * Enabled nodes of this shard with their PromQL queries: loaded from node-registry at startup, then kept
 * current from node.changed.
 *
 * Consumer: a durable push consumer per shard ("feature-aggregator-<index>") with manual ack and DeliverPolicy.ALL.
 * Durable: JetStream remembers what this shard has acked, so events published while the replica was down are
 * delivered after a restart. ALL only matters on the first run (replays history; harmless, every event is applied
 * idempotently from the registry's current state). Each shard has its own durable, hence every shard sees every
 * event and filters by ownership. We subscribe first and load the registry second, so no event falls in the gap.
 */
@Component
public class NodeCache {
    private static final Logger log = LoggerFactory.getLogger(NodeCache.class);

    private final Map<String, Map<String, String>> nodes = new ConcurrentHashMap<>();
    private final RestClient registry;
    private final Connection connection;
    private final JetStream js;
    private final int shardIndex, shardCount;

    public NodeCache(@Value("${registry.url}") String registryUrl, Connection connection, JetStream js,
                     @Value("${shard.index}") int shardIndex, @Value("${shard.count}") int shardCount) {
        this.registry = RestClient.builder().baseUrl(registryUrl).build();
        this.connection = connection;
        this.js = js;
        this.shardIndex = shardIndex;
        this.shardCount = shardCount;
    }

    /** node_id -> (metric -> PromQL), a copy that is safe to iterate. */
    public Map<String, Map<String, String>> snapshot() {
        return Map.copyOf(nodes);
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() throws Exception {
        js.subscribe(Subjects.NODE_CHANGED, connection.createDispatcher(), this::onMessage, false,
                PushSubscribeOptions.builder().durable("feature-aggregator-" + shardIndex)
                        .configuration(ConsumerConfiguration.builder().deliverPolicy(DeliverPolicy.All)
                                .ackWait(Duration.ofSeconds(30)).build()).build());
        while (true) {
            try {
                List<Map<String, Object>> list = registry.get().uri("/v1/nodes?enabled=true").retrieve()
                        .body(new ParameterizedTypeReference<>() {});
                for (Map<String, Object> n : list) refresh((String) n.get("node_id"));
                log.info("loaded {} node(s) for shard {}/{}: {}", nodes.size(), shardIndex, shardCount, nodes.keySet());
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
            String id = p.path("node_id").asText();
            if (p.path("op").asText().equals("deleted") || !p.path("enabled").asBoolean()) nodes.remove(id);
            else refresh(id);
            m.ack();
        } catch (Exception e) {
            log.warn("node.changed failed, redelivery in 5 s: {}", e.toString());
            m.nakWithDelay(Duration.ofSeconds(5));
        }
    }

    /** Fetches the node's queries from the registry; a vanished node (404) is dropped. */
    private void refresh(String id) {
        if (!Sharding.owns(id, shardIndex, shardCount)) return;
        try {
            nodes.put(id, registry.get().uri("/v1/nodes/{id}/queries", id).retrieve()
                    .body(new ParameterizedTypeReference<Map<String, String>>() {}));
        } catch (HttpClientErrorException.NotFound e) {
            nodes.remove(id);
        }
    }
}
