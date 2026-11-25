package ua.lab3.inference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.EventPublisher;
import ua.lab3.common.Subjects;

/**
 * Role "inference": features.built -> score.computed.
 *
 * features.built: ONE durable pull consumer "fuzzy-inference" shared by all replicas, so each message goes to exactly
 * one replica (work is split); explicit ack, nak on failure, a stopped service just lets messages pile up in the consumer.
 * model.activated: plain NATS subscription (no queue group), so EVERY replica gets it and reloads that version.
 * shortcut: a replica that misses model.activated (it was down/disconnected) keeps the old model until the next activation.
 */
@Component
@Profile("inference")
public class InferenceEngine {
    private static final Logger log = LoggerFactory.getLogger(InferenceEngine.class);
    private static final String HOST = System.getenv().getOrDefault("HOSTNAME", "local");

    private final AtomicReference<Model> model = new AtomicReference<>();
    private final AtomicReference<List<String>> lastFeatures = new AtomicReference<>();
    private final ModelService models;
    private final Connection connection;
    private final JetStream js;
    private final EventPublisher events;
    private volatile boolean running = true;

    InferenceEngine(ModelService models, Connection connection, JetStream js, EventPublisher events) {
        this.models = models;
        this.connection = connection;
        this.js = js;
        this.events = events;
    }

    public Model model() {
        return model.get();
    }

    /** Feature names of the latest features.built this replica saw (null before the first one). */
    public List<String> lastFeatures() {
        return lastFeatures.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        connection.createDispatcher(this::onActivated).subscribe(Subjects.MODEL_ACTIVATED);
        Thread.ofPlatform().name("features-consumer").daemon().start(this::consume);
    }

    @PreDestroy
    void stop() {
        running = false;
    }

    private void onActivated(Message m) {
        try {
            int v = EventEnvelope.MAPPER.readTree(m.getData()).path("payload").path("version").asInt();
            model.set(models.get(v));
            log.info("model v{} loaded (replica {})", v, HOST);
        } catch (Exception e) {
            log.error("cannot reload model after model.activated: {}", e.toString());
        }
    }

    private void consume() {
        try {
            model.set(models.awaitActive());
            log.info("starting with model v{} (replica {})", model.get().version(), HOST);
            JetStreamSubscription sub = js.subscribe(Subjects.FEATURES_BUILT, PullSubscribeOptions.builder()
                    .durable("fuzzy-inference").configuration(ConsumerConfiguration.builder()
                            .deliverPolicy(DeliverPolicy.New).ackWait(Duration.ofSeconds(30)).build()).build());
            while (running) {
                try {
                    sub.fetch(10, Duration.ofSeconds(1)).forEach(this::handle);
                } catch (Exception e) {
                    log.warn("fetch failed: {}", e.toString());
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            log.error("consumer stopped: {}", e.toString());
        }
    }

    private void handle(Message m) {
        try {
            JsonNode p = EventEnvelope.MAPPER.readTree(m.getData()).path("payload");
            List<String> names = EventEnvelope.MAPPER.convertValue(p.path("feature_names"), new TypeReference<>() {});
            List<Double> vector = EventEnvelope.MAPPER.convertValue(p.path("vector"), new TypeReference<>() {});
            lastFeatures.set(names);
            Model mdl = model.get();
            List<String> missing = mdl.missing(names);
            if (!missing.isEmpty()) {
                log.warn("skipping window: vector lacks features {} needed by model v{}", missing, mdl.version());
                m.ack();
                return;
            }
            String node = p.path("node_id").asText(), window = p.path("window_start").asText();
            Model.Result r = mdl.infer(Model.asMap(names, vector));
            // deterministic id + Nats-Msg-Id: a redelivered window cannot create a second analysis
            String analysisId = UUID.nameUUIDFromBytes((node + "|" + window + "|" + mdl.version()).getBytes()).toString();
            events.publish(Subjects.SCORE_COMPUTED, EventEnvelope.of(Subjects.SCORE_COMPUTED, Map.of(
                    "analysis_id", analysisId, "node_id", node, "window_start", window, "score", r.score(),
                    "active_rules", r.activeRules(), "model_version", mdl.version(),
                    "vector", vector, "feature_names", names)), analysisId);
            log.info("scored {} window={} score={} rules={} model=v{} replica={}", node, window, r.score(),
                    r.activeRules().stream().map(a -> a.id() + ":" + a.strength()).toList(), mdl.version(), HOST);
            m.ack();
        } catch (Exception e) {
            log.warn("scoring failed, redelivery in 5 s: {}", e.toString());
            m.nakWithDelay(Duration.ofSeconds(5));
        }
    }
}
