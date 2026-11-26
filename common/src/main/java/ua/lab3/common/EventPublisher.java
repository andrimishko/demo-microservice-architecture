package ua.lab3.common;

import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import java.nio.charset.StandardCharsets;

public class EventPublisher {
    private final JetStream js;

    public EventPublisher(JetStream js) {
        this.js = js;
    }

    /** Publishes the envelope; Nats-Msg-Id = event_id lets JetStream drop duplicates. */
    public PublishAck publish(String subject, EventEnvelope event) {
        return publish(subject, event, event.eventId().toString());
    }

    /** Same, but with a caller-chosen dedup id (e.g. a deterministic id per time window). */
    public PublishAck publish(String subject, EventEnvelope event, String msgId) {
        try {
            Headers h = new Headers().add("Nats-Msg-Id", msgId);
            return js.publish(subject, h, EventEnvelope.MAPPER.writeValueAsString(event).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BrokerUnavailableException("publish to " + subject + " failed", e);
        }
    }
}
