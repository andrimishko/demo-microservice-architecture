package ua.lab3.aggregator;

import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.Subjects;

/** Every replica sees model.activated (plain subscription, no queue group); only logs it and checks the schema version. */
@Component
class ModelActivationLogger {
    private static final Logger log = LoggerFactory.getLogger(ModelActivationLogger.class);

    private final Connection connection;

    ModelActivationLogger(Connection connection) {
        this.connection = connection;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        connection.createDispatcher(m -> {
            try {
                var p = EventEnvelope.MAPPER.readTree(m.getData()).path("payload");
                int schema = p.path("schema_version").asInt();
                log.info("model v{} activated (schema_version {})", p.path("version").asInt(), schema);
                if (schema != FeatureBuilder.SCHEMA_VERSION)
                    log.warn("model schema_version {} differs from ours ({})", schema, FeatureBuilder.SCHEMA_VERSION);
            } catch (Exception e) {
                log.warn("unreadable model.activated: {}", e.toString());
            }
        }).subscribe(Subjects.MODEL_ACTIVATED);
    }
}
