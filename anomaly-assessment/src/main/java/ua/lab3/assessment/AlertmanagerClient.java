package ua.lab3.assessment;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Anti-corruption layer for Alertmanager (POST /api/v2/alerts). */
@Component
class AlertmanagerClient implements AlertSender {
    private final RestClient http;

    AlertmanagerClient(@Value("${alertmanager.url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public void send(Alert a) {
        Map<String, Object> body = Map.of(
                "labels", Map.of("alertname", "NodeAnomaly", "node_id", a.nodeId(), "state", a.state().name(),
                        "priority", a.priority().name()),
                "annotations", Map.of("score", String.valueOf(a.score()), "active_rules", a.activeRules(),
                        "analysis_id", a.analysisId(), "feedback_url", "/api/v1/feedback?analysis_id=" + a.analysisId()),
                "startsAt", a.sentAt().toString());
        http.post().uri("/api/v2/alerts").body(List.of(body)).retrieve().toBodilessEntity();
    }
}
