package ua.lab3.assessment;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.Subjects;
import ua.lab3.common.events.FeedbackSubmitted;

/** feedback.submitted -> alert lifecycle (acknowledge / close). Durable push consumer "anomaly-assessment-feedback". */
@Component
class FeedbackListener {
    private static final Logger log = LoggerFactory.getLogger(FeedbackListener.class);

    private final AlertService alerts;
    private final Connection connection;
    private final JetStream js;

    FeedbackListener(AlertService alerts, Connection connection, JetStream js) {
        this.alerts = alerts;
        this.connection = connection;
        this.js = js;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() throws Exception {
        js.subscribe(Subjects.FEEDBACK_SUBMITTED, connection.createDispatcher(), this::onMessage, false,
                PushSubscribeOptions.builder().durable("anomaly-assessment-feedback")
                        .configuration(ConsumerConfiguration.builder().deliverPolicy(DeliverPolicy.New)
                                .ackWait(Duration.ofSeconds(30)).build()).build());
    }

    private void onMessage(Message m) {
        try {
            FeedbackSubmitted f = EventEnvelope.MAPPER.treeToValue(
                    EventEnvelope.MAPPER.readTree(m.getData()).path("payload"), FeedbackSubmitted.class);
            alerts.onFeedback(f.nodeId(), f.analysisId(), f.verdict(), f.cause() == null ? "UNKNOWN" : f.cause());
        } catch (Exception e) {
            log.warn("bad feedback skipped: {}", e.toString()); // retrying a malformed message would not help
        }
        m.ack();
    }
}
