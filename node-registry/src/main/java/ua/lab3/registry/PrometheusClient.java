package ua.lab3.registry;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Anti-corruption layer: the rest of the service never sees Prometheus JSON. */
@Component
public class PrometheusClient {
    private final RestClient http;

    public PrometheusClient(@Value("${prometheus.url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** True if Prometheus knows the instance, i.e. `up{instance="<id>"}` returns a non-empty vector. */
    public boolean hasMetrics(String nodeId) {
        try {
            JsonNode body = http.get()
                    .uri(u -> u.path("/api/v1/query").queryParam("query", "{q}").build("up{instance=\"" + nodeId + "\"}"))
                    .retrieve().body(JsonNode.class);
            return body != null && !body.path("data").path("result").isEmpty();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Prometheus is unavailable", e);
        }
    }
}
