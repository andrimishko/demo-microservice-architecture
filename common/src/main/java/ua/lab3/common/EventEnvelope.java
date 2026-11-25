package ua.lab3.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record EventEnvelope(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("payload") JsonNode payload) {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public static EventEnvelope of(String type, Object payload) {
        return new EventEnvelope(UUID.randomUUID(), type, Instant.now().truncatedTo(ChronoUnit.MILLIS),
                1, MAPPER.valueToTree(payload));
    }
}
