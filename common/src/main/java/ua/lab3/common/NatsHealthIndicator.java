package ua.lab3.common;

import io.nats.client.Connection;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class NatsHealthIndicator extends AbstractHealthIndicator {
    private final Connection connection;

    public NatsHealthIndicator(Connection connection) {
        this.connection = connection;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Connection.Status s = connection.getStatus();
        (s == Connection.Status.CONNECTED ? builder.up() : builder.down()).withDetail("status", s.name());
    }
}
