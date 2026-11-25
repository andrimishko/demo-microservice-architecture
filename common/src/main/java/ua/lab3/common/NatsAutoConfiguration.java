package ua.lab3.common;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class NatsAutoConfiguration {

    @Bean(destroyMethod = "close")
    Connection natsConnection(@Value("${nats.url:nats://nats:4222}") String url) throws IOException, InterruptedException {
        return Nats.connect(new Options.Builder().server(url).maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(2)).build());
    }

    @Bean
    JetStream jetStream(Connection c) throws IOException {
        return c.jetStream();
    }

    @Bean
    EventPublisher eventPublisher(JetStream js) {
        return new EventPublisher(js);
    }

    @Bean
    NatsHealthIndicator natsHealthIndicator(Connection c) {
        return new NatsHealthIndicator(c);
    }

    /** Idempotent: update if the stream exists, create otherwise (10059 = stream not found). */
    @Bean
    ApplicationRunner detectionStream(Connection c) {
        return args -> {
            JetStreamManagement jsm = c.jetStreamManagement();
            StreamConfiguration cfg = StreamConfiguration.builder().name(Subjects.STREAM)
                    .subjects(Subjects.ALL).storageType(StorageType.File)
                    .duplicateWindow(Duration.ofMinutes(2)).build();
            try {
                jsm.updateStream(cfg);
            } catch (JetStreamApiException e) {
                if (e.getApiErrorCode() != 10059) throw e;
                jsm.addStream(cfg);
            }
        };
    }
}
