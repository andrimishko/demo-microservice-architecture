package ua.lab3.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import io.nats.client.JetStream;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.EventPublisher;
import ua.lab3.common.Subjects;

/**
 * score.computed -> state.classified. Durable pull consumer "anomaly-assessment", explicit ack, nak on failure.
 * Steps per message: classify, save the result, publish state.classified (Nats-Msg-Id = state.classified:<analysis_id>), update alerts.
 */
@Component
class ScoreConsumer {
    private static final Logger log = LoggerFactory.getLogger(ScoreConsumer.class);

    private final ThresholdsCache thresholds;
    private final ResultStore store;
    private final AlertService alerts;
    private final JetStream js;
    private final EventPublisher events;
    private volatile boolean running = true;

    ScoreConsumer(ThresholdsCache thresholds, ResultStore store, AlertService alerts, JetStream js, EventPublisher events) {
        this.thresholds = thresholds;
        this.store = store;
        this.alerts = alerts;
        this.js = js;
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        Thread.ofPlatform().name("score-consumer").daemon().start(this::consume);
    }

    @PreDestroy
    void stop() {
        running = false;
    }

    private void consume() {
        try {
            thresholds.awaitLoaded(); // otherwise the first scores would hit an empty cache and be dropped as "unknown node"
            JetStreamSubscription sub = js.subscribe(Subjects.SCORE_COMPUTED, PullSubscribeOptions.builder()
                    .durable("anomaly-assessment").configuration(ConsumerConfiguration.builder()
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
            String node = p.path("node_id").asText(), analysisId = p.path("analysis_id").asText();
            var t = thresholds.get(node);
            if (t.isEmpty()) {
                log.warn("score for unknown or disabled node {} skipped", node);
                m.ack();
                return;
            }
            double score = p.path("score").asDouble();
            State state = ThresholdPolicy.classify(score, t.get().warning(), t.get().anomaly());
            Classified result = new Classified(analysisId, node, p.path("window_start").asText(), state, score,
                    p.path("model_version").asInt(), p.path("vector"), p.path("feature_names"));
            store.save(result);
            events.publish(Subjects.STATE_CLASSIFIED, EventEnvelope.of(Subjects.STATE_CLASSIFIED, result),
                    Subjects.STATE_CLASSIFIED + ":" + analysisId); // dedup is per stream: a bare analysis_id is score.computed's id too
            List<String> rules = new ArrayList<>();
            p.path("active_rules").forEach(r -> rules.add(r.path("id").asText()));
            alerts.onState(node, analysisId, state, score, String.join(",", rules));
            log.info("classified {} window={} score={} -> {}", node, result.windowStart(), score, state);
            m.ack();
        } catch (Exception e) {
            log.warn("classification failed, redelivery in 5 s: {}", e.toString());
            m.nakWithDelay(Duration.ofSeconds(5));
        }
    }
}
