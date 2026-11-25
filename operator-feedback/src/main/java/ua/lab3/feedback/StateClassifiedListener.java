package ua.lab3.feedback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.Subjects;

/**
 * state.classified -> analysis_snapshot. Durable push consumer "operator-feedback": while this service is down the
 * messages wait in the consumer and are stored after the restart. Idempotent: a known analysis_id is skipped.
 */
@Component
class StateClassifiedListener {
    private static final Logger log = LoggerFactory.getLogger(StateClassifiedListener.class);

    private final SnapshotRepository snapshots;
    private final Connection connection;
    private final JetStream js;

    StateClassifiedListener(SnapshotRepository snapshots, Connection connection, JetStream js) {
        this.snapshots = snapshots;
        this.connection = connection;
        this.js = js;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() throws Exception {
        js.subscribe(Subjects.STATE_CLASSIFIED, connection.createDispatcher(), this::onMessage, false,
                PushSubscribeOptions.builder().durable("operator-feedback")
                        .configuration(ConsumerConfiguration.builder().deliverPolicy(DeliverPolicy.All)
                                .ackWait(Duration.ofSeconds(30)).build()).build());
    }

    private void onMessage(Message m) {
        SnapshotEntity s;
        try {
            JsonNode p = EventEnvelope.MAPPER.readTree(m.getData()).path("payload");
            s = new SnapshotEntity(p.path("analysis_id").asText(), p.path("node_id").asText(),
                    Instant.parse(p.path("window_start").asText()), p.path("state").asText(), p.path("score").asDouble(),
                    p.path("model_version").asInt(),
                    EventEnvelope.MAPPER.convertValue(p.path("vector"), new TypeReference<List<Double>>() {}),
                    EventEnvelope.MAPPER.convertValue(p.path("feature_names"), new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            log.warn("bad state.classified skipped: {}", e.toString()); // retrying a malformed message would not help
            m.ack();
            return;
        }
        try {
            if (!snapshots.existsById(s.analysisId())) snapshots.save(s);
            m.ack();
        } catch (Exception e) {
            log.warn("snapshot {} not saved, redelivery in 5 s: {}", s.analysisId(), e.toString());
            m.nakWithDelay(Duration.ofSeconds(5));
        }
    }
}
