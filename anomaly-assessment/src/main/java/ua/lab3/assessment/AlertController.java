package ua.lab3.assessment;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Debug view of the in-memory alerts; internal only. */
@RestController
class AlertController {
    private final AlertService alerts;

    AlertController(AlertService alerts) {
        this.alerts = alerts;
    }

    @GetMapping("/v1/alerts")
    List<Alert> list() {
        return alerts.all();
    }
}
