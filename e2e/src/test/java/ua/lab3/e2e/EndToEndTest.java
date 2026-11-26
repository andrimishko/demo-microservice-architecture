package ua.lab3.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** Black-box test of the running stack through the gateway. Run with `make e2e`; restores scenarios and model v1 afterwards. */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndTest {
    static final String GW = System.getProperty("e2e.gateway", "http://localhost:8000") + "/api/v1";
    static final String MOCK = System.getProperty("e2e.mock", "http://localhost:8090");
    static final String ADMIN = "admin-demo-key", ENGINEER = "engineer-demo-key";
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();

    record Resp(int status, JsonNode body) {}

    static Resp call(String method, String url, String json, String key) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
        if (key != null) b.header("X-API-Key", key);
        b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body().isBlank() ? JSON.nullNode() : JSON.readTree(r.body()));
    }

    static Resp api(String method, String path, String json, String key) throws Exception {
        return call(method, GW + path, json, key);
    }

    static void scenario(String node, String name) throws Exception {
        assertEquals(200, call("POST", MOCK + "/scenario", "{\"node_id\":\"" + node + "\",\"scenario\":\"" + name + "\"}", null).status());
    }

    static void await(String what, int seconds, Supplier<Boolean> cond) throws Exception {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                if (cond.get()) return;
            } catch (RuntimeException ignored) { // not ready yet, poll again
            }
            Thread.sleep(2000);
        }
        throw new AssertionError("timeout after " + seconds + " s waiting for: " + what);
    }

    static JsonNode get(String path) {
        try {
            return api("GET", path, null, ADMIN).body();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static int activeModel() {
        for (JsonNode m : get("/models")) if (m.path("status").asText().equals("ACTIVE")) return m.path("version").asInt();
        return -1;
    }

    /** The newest classified window of a node (the mock keeps them in insertion order). */
    static JsonNode lastResult(String node) throws Exception {
        JsonNode last = null;
        for (JsonNode h : call("POST", MOCK + "/analysis-results/_search", "{}", null).body().path("hits").path("hits"))
            if (h.path("_source").path("node_id").asText().equals(node)) last = h.path("_source");
        if (last == null) throw new IllegalStateException("no results for " + node);
        return last;
    }

    static JsonNode openAlert(String node, String state) {
        JsonNode found = null;
        for (JsonNode a : get("/alerts"))
            if (a.path("nodeId").asText().equals(node) && a.path("state").asText().equals(state)
                    && !a.path("status").asText().equals("CLOSED")) found = a;
        return found;
    }

    @BeforeAll
    static void clean() throws Exception {
        scenario("node-1", "NORMAL");
        if (activeModel() != 1) assertEquals(200, api("POST", "/models/1/activate", null, ADMIN).status());
        await("node-1 has no open alert", 60, () -> openAlert("node-1", "WARNING") == null && openAlert("node-1", "ANOMALY") == null);
    }

    @AfterAll
    static void restore() throws Exception {
        scenario("node-1", "NORMAL");
        if (activeModel() != 1) api("POST", "/models/1/activate", null, ADMIN);
    }

    @Test @Order(1)
    void authentication() throws Exception {
        assertEquals(401, api("GET", "/nodes", null, null).status());
        assertEquals(401, api("GET", "/nodes", null, "wrong-key").status());
        assertEquals(200, api("GET", "/nodes", null, ENGINEER).status());
        assertEquals(403, api("POST", "/nodes", "{}", ENGINEER).status());
        assertEquals(403, api("POST", "/models/1/activate", null, ENGINEER).status());
    }

    @Test @Order(2)
    void nodeCrud() throws Exception {
        api("DELETE", "/nodes/node-3", null, ADMIN); // may or may not exist
        String body = "{\"node_id\":\"node-3\",\"name\":\"crud\",\"type\":\"SPRING_BOOT\",\"thresholds\":{\"warning\":0.5,\"anomaly\":0.8}}";
        assertEquals(201, api("POST", "/nodes", body, ADMIN).status());
        assertEquals(409, api("POST", "/nodes", body, ADMIN).status());
        assertEquals(200, api("GET", "/nodes/node-3", null, ENGINEER).status());
        Resp patched = api("PATCH", "/nodes/node-3", "{\"thresholds\":{\"warning\":0.4,\"anomaly\":0.7}}", ADMIN);
        assertEquals(200, patched.status());
        assertEquals(0.4, patched.body().path("thresholds").path("warning").asDouble());
        assertEquals(400, api("POST", "/nodes", body.replace("node-3", "node-x").replace("0.5", "0.9"), ADMIN).status()); // warning > anomaly
        assertEquals(422, api("POST", "/nodes", body.replace("node-3", "ghost"), ADMIN).status()); // no metrics for it
        assertEquals(204, api("DELETE", "/nodes/node-3", null, ADMIN).status());
        assertEquals(404, api("GET", "/nodes/node-3", null, ADMIN).status());
    }

    @Test @Order(3)
    void falsePositiveLoop() throws Exception {
        int before = activeModel();
        scenario("node-1", "NOISY_LOGS");
        await("WARNING alert on node-1", 90, () -> openAlert("node-1", "WARNING") != null);
        String analysis = openAlert("node-1", "WARNING").path("analysisId").asText();
        assertTrue(call("GET", MOCK + "/api/v2/alerts", null, null).body().toString().contains(analysis), "alert reached Alertmanager");

        String fb = "{\"analysis_id\":\"" + analysis + "\",\"verdict\":\"FALSE_POSITIVE\",\"cause\":\"NOISY_LOGS\"}";
        await("snapshot stored by operator-feedback", 40, () -> {
            try {
                return api("POST", "/feedback", fb, ENGINEER).status() != 404;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(409, api("POST", "/feedback", fb, ENGINEER).status(), "duplicate feedback");

        await("a new model version is active", 60, () -> activeModel() > before);
        int after = activeModel();
        await("NORMAL windows scored by the new model", 90, () -> {
            try {
                JsonNode r = lastResult("node-1");
                return r.path("model_version").asInt() >= after && r.path("state").asText().equals("NORMAL");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(lastResult("node-1").path("score").asDouble() < 0.5);
    }

    @Test @Order(4)
    void stats() throws Exception {
        Resp r = api("GET", "/feedback/stats?node_id=node-1&period=PT1H", null, ENGINEER);
        assertEquals(200, r.status());
        assertTrue(r.body().path("false_positive").asLong() >= 1);
        assertTrue(r.body().path("top_causes").has("NOISY_LOGS"));
        assertEquals(400, api("GET", "/feedback/stats?node_id=node-1&period=bogus", null, ENGINEER).status());
    }
}
