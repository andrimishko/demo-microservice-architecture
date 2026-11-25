package ua.lab3.mock;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Demo control: switch a node between scenarios. */
@RestController
public class ScenarioController {
    record ScenarioRequest(@JsonProperty("node_id") String nodeId, Scenario scenario) {}

    private final MockData data;

    ScenarioController(MockData data) {
        this.data = data;
    }

    @GetMapping("/scenario")
    Map<String, Scenario> all() {
        return data.scenarios();
    }

    @PostMapping("/scenario")
    Map<String, Scenario> set(@RequestBody ScenarioRequest req) {
        if (req.nodeId() == null || req.scenario() == null || !data.known(req.nodeId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown node or missing scenario");
        data.setScenario(req.nodeId(), req.scenario());
        return data.scenarios();
    }
}
