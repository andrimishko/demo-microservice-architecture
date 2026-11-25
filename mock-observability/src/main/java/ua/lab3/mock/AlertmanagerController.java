package ua.lab3.mock;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Imitates the Alertmanager v2 alerts API: stores what it receives, nothing is ever routed or resolved. */
@RestController
public class AlertmanagerController {
    private static final Logger log = LoggerFactory.getLogger(AlertmanagerController.class);
    private final List<JsonNode> alerts = new CopyOnWriteArrayList<>();

    @PostMapping("/api/v2/alerts")
    void receive(@RequestBody List<JsonNode> received) {
        received.forEach(a -> log.info("alert received: {}", a));
        alerts.addAll(received);
    }

    @GetMapping("/api/v2/alerts")
    List<JsonNode> list() {
        return alerts;
    }
}
