package ua.lab3.inference;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.Subjects;
import ua.lab3.common.events.FeedbackSubmitted;

/**
 * Role "adaptation" (exactly one replica): feedback.submitted -> adapted copy of the active model saved as the next
 * version and activated. Durable push consumer, so feedback sent while this service was down is still applied.
 * shortcut: a redelivered feedback (ack lost) would be applied twice; dedup by feedback_id when that matters.
 */
@Component
@Profile("adaptation")
class FeedbackConsumer {
    private static final Logger log = LoggerFactory.getLogger(FeedbackConsumer.class);

    private final ModelService models;
    private final Connection connection;
    private final JetStream js;
    private final double eta;

    FeedbackConsumer(ModelService models, Connection connection, JetStream js, @Value("${adaptation.eta}") double eta) {
        this.models = models;
        this.connection = connection;
        this.js = js;
        this.eta = eta;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() throws Exception {
        log.info("starting with model v{}, eta={}", models.awaitActive().version(), eta);
        js.subscribe(Subjects.FEEDBACK_SUBMITTED, connection.createDispatcher(), this::onMessage, false,
                PushSubscribeOptions.builder().durable("fuzzy-adaptation").configuration(ConsumerConfiguration.builder()
                        .deliverPolicy(DeliverPolicy.New).ackWait(Duration.ofSeconds(30)).build()).build());
    }

    private void onMessage(Message m) {
        try {
            FeedbackSubmitted f = EventEnvelope.MAPPER.treeToValue(
                    EventEnvelope.MAPPER.readTree(m.getData()).path("payload"), FeedbackSubmitted.class);
            apply(f);
            m.ack();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("bad feedback skipped: {}", e.getMessage()); // retrying a malformed message would not help
            m.ack();
        } catch (Exception e) {
            log.warn("feedback failed, redelivery in 5 s: {}", e.toString());
            m.nakWithDelay(Duration.ofSeconds(5));
        }
    }

    private void apply(FeedbackSubmitted f) {
        Model old = models.loadActive();
        if (f.vector() == null || f.featureNames() == null || f.vector().size() != f.featureNames().size()
                || !old.missing(f.featureNames()).isEmpty())
            throw new IllegalArgumentException("feedback " + f.feedbackId() + " has no usable vector for model v" + old.version());
        Model adapted = Adaptation.adapt(old, Model.asMap(f.featureNames(), f.vector()), f.verdict(), eta);
        List<String> changes = new ArrayList<>();
        for (int i = 0; i < old.rules().size(); i++) {
            double before = old.rules().get(i).consequent(), after = adapted.rules().get(i).consequent();
            if (before != after) changes.add(old.rules().get(i).id() + " " + before + " -> " + after);
        }
        Model saved = models.saveNext(adapted);
        models.activate(saved.version());
        log.info("feedback {} ({} on {}) turned v{} into v{}; changed consequents: {}", f.feedbackId(), f.verdict(),
                f.nodeId(), old.version(), saved.version(), changes);
    }
}
