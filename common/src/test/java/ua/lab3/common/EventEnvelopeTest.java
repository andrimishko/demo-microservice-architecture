package ua.lab3.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import io.nats.client.JetStream;
import io.nats.client.impl.Headers;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventEnvelopeTest {

    @Test
    void jsonRoundTripUsesSnakeCaseFields() throws Exception {
        EventEnvelope e = EventEnvelope.of("node.changed", Map.of("node_id", "n1"));
        String json = EventEnvelope.MAPPER.writeValueAsString(e);
        JsonNode n = EventEnvelope.MAPPER.readTree(json);
        assertTrue(n.has("event_id") && n.has("event_type") && n.has("occurred_at")
                && n.has("schema_version") && n.has("payload"));
        assertEquals(5, n.size());
        assertTrue(n.get("occurred_at").isTextual());
        assertEquals(e, EventEnvelope.MAPPER.readValue(json, EventEnvelope.class));
    }

    @Test
    void publisherSetsMsgIdHeader() throws Exception {
        JetStream js = mock(JetStream.class);
        EventEnvelope e = EventEnvelope.of("features.built", Map.of("a", 1));
        new EventPublisher(js).publish(Subjects.FEATURES_BUILT, e);
        ArgumentCaptor<Headers> h = ArgumentCaptor.forClass(Headers.class);
        verify(js).publish(eq(Subjects.FEATURES_BUILT), h.capture(), any(byte[].class));
        assertEquals(List.of(e.eventId().toString()), h.getValue().get("Nats-Msg-Id"));
    }
}
