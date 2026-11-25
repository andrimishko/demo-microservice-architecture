package ua.lab3.inference;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.EventPublisher;
import ua.lab3.common.Subjects;

/** Model lifecycle shared by both roles: seed, upload as the next version, activate (pointer + event). */
@Component
public class ModelService {
    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    private final ModelRepository repo;
    private final EventPublisher events;

    ModelService(ModelRepository repo, EventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    /** The active model; on a pristine bucket uploads the seed v1 first. */
    public Model loadActive() {
        OptionalInt v = repo.activeVersion();
        if (v.isPresent()) return repo.load(v.getAsInt());
        try (var in = getClass().getResourceAsStream("/seed-model.json")) {
            Model seed = Model.parse(in.readAllBytes());
            repo.save(seed);
            repo.setActive(seed.version());
            log.info("bucket was empty: seeded model v{}", seed.version());
            return seed;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Startup helper: MinIO may come up a moment later than we do. */
    public Model awaitActive() throws InterruptedException {
        while (true) {
            try {
                return loadActive();
            } catch (Exception e) {
                log.warn("model store not ready ({}), retrying in 3 s", e.toString());
                Thread.sleep(3000);
            }
        }
    }

    public OptionalInt activeVersion() {
        return repo.activeVersion();
    }

    public List<Integer> versions() {
        return repo.versions();
    }

    public Model get(int version) {
        return repo.load(version);
    }

    /** Stores the model as max+1 (the version inside the file is ignored).
     *  shortcut: two concurrent uploads could pick the same number; a single operator/adapter is assumed. */
    public Model saveNext(Model m) {
        Model next = m.withVersion(repo.versions().stream().max(Integer::compare).orElse(0) + 1);
        repo.save(next);
        return next;
    }

    public void activate(int version) {
        Model m = repo.load(version);
        repo.setActive(version);
        events.publish(Subjects.MODEL_ACTIVATED, EventEnvelope.of(Subjects.MODEL_ACTIVATED, Map.of(
                "version", version, "schema_version", m.schemaVersion(),
                "templates", List.of(), "normalization_bounds", m.normalization())));
        log.info("model v{} activated", version);
    }
}
