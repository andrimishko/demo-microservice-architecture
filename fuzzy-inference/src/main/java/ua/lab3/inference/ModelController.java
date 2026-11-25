package ua.lab3.inference;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Model management API (errors become RFC 9457 problem+json via spring.mvc.problemdetails). */
@RestController
@RequestMapping("/v1")
@Profile("inference")
class ModelController {
    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    record ScoreRequest(List<Double> vector, @JsonProperty("feature_names") List<String> featureNames) {}

    private final ModelService models;
    private final InferenceEngine engine;

    ModelController(ModelService models, InferenceEngine engine) {
        this.models = models;
        this.engine = engine;
    }

    @PostMapping("/models")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> upload(@RequestPart("file") MultipartFile file) throws IOException {
        Model m;
        try {
            m = Model.parse(file.getBytes());
        } catch (IllegalArgumentException | IOException e) { // Jackson's parse errors are IOExceptions
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid model: " + e.getMessage());
        }
        List<String> seen = engine.lastFeatures();
        if (seen == null) log.warn("no features.built seen yet, cannot check the uploaded model's feature_names");
        else if (!m.missing(seen).isEmpty())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "model uses features not produced by feature-aggregator: " + m.missing(seen));
        Model saved = models.saveNext(m);
        return Map.of("version", saved.version(), "schema_version", saved.schemaVersion(), "status", "UPLOADED");
    }

    @PostMapping("/models/{v}/activate")
    Map<String, Object> activate(@PathVariable int v) {
        if (!models.versions().contains(v)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no model v" + v);
        if (models.activeVersion().orElse(-1) == v) throw new ResponseStatusException(HttpStatus.CONFLICT, "v" + v + " is already active");
        models.activate(v);
        return Map.of("version", v, "status", "ACTIVE");
    }

    @GetMapping("/models")
    List<Map<String, Object>> list() {
        int active = models.activeVersion().orElse(-1);
        return models.versions().stream()
                .map(v -> Map.<String, Object>of("version", v, "status", v == active ? "ACTIVE" : "UPLOADED")).toList();
    }

    @GetMapping("/models/active")
    Model active() {
        return models.get(models.activeVersion().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no active model")));
    }

    /** The k rules with the highest consequent (the ones that push the score up most); all rules without top. */
    @GetMapping("/models/{v}/rules")
    List<String> rules(@PathVariable int v, @RequestParam(required = false) Integer top) {
        if (!models.versions().contains(v)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no model v" + v);
        var stream = models.get(v).rules().stream().sorted((a, b) -> Double.compare(b.consequent(), a.consequent()));
        return (top == null ? stream : stream.limit(top)).map(Model::render).toList();
    }

    /** Diagnostic only: scores a vector with this replica's current model, publishes nothing. */
    @PostMapping("/score")
    Model.Result score(@RequestBody ScoreRequest req) {
        Model m = engine.model();
        if (req.vector() == null || req.featureNames() == null || req.vector().size() != req.featureNames().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vector and feature_names must have the same length");
        if (!m.missing(req.featureNames()).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing features: " + m.missing(req.featureNames()));
        return m.infer(Model.asMap(req.featureNames(), req.vector()));
    }
}
