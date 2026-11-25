package ua.lab3.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.EventPublisher;
import ua.lab3.common.Subjects;
import ua.lab3.registry.Dtos.CreateNode;

class NodeServiceTest {
    private final NodeRepository repo = mock(NodeRepository.class);
    private final PrometheusClient prometheus = mock(PrometheusClient.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final NodeService service = new NodeService(repo, prometheus, events);
    private final CreateNode req = new CreateNode("node-1", "API", NodeType.SPRING_BOOT, new Thresholds(0.5, 0.8));

    private HttpStatus statusOf(Runnable r) {
        return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode();
    }

    @Test
    void createSavesAndPublishesEvent() {
        when(prometheus.hasMetrics("node-1")).thenReturn(true);
        when(repo.save(any(Node.class))).thenAnswer(i -> i.getArgument(0));

        service.create(req);

        ArgumentCaptor<EventEnvelope> event = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(events).publish(eq(Subjects.NODE_CHANGED), event.capture());
        assertEquals("created", event.getValue().payload().get("op").asText());
        assertEquals("node-1", event.getValue().payload().get("node_id").asText());
    }

    @Test
    void createExistingNodeIs409() {
        when(repo.existsById("node-1")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.create(req)));
        verify(events, never()).publish(any(), any());
    }

    @Test
    void createWithoutMetricsIs422() {
        when(prometheus.hasMetrics("node-1")).thenReturn(false);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, statusOf(() -> service.create(req)));
        verify(repo, never()).save(any());
        verify(events, never()).publish(any(), any());
    }

    @Test
    void thresholdsMustBeInRangeAndOrdered() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertTrue(validator.validate(new Thresholds(0.5, 0.8)).isEmpty());
        assertFalse(validator.validate(new Thresholds(0.8, 0.8)).isEmpty());
        assertFalse(validator.validate(new Thresholds(0.9, 0.5)).isEmpty());
        assertFalse(validator.validate(new Thresholds(-0.1, 0.5)).isEmpty());
        assertFalse(validator.validate(new Thresholds(0.5, 1.5)).isEmpty());
    }
}
