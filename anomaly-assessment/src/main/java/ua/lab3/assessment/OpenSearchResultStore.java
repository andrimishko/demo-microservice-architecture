package ua.lab3.assessment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Anti-corruption layer for OpenSearch: indexes one document per analysis. */
@Component
class OpenSearchResultStore implements ResultStore {
    private final RestClient http;

    OpenSearchResultStore(@Value("${opensearch.url}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    // shortcut: random document id, so a redelivered score is indexed twice; upgrade: PUT /_doc/{analysis_id}.
    @Override
    public void save(Classified result) {
        http.post().uri("/analysis-results/_doc").body(result).retrieve().toBodilessEntity();
    }
}
