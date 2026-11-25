package ua.lab3.aggregator;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.lab3.aggregator.FeatureBuilder.FeaturesBuilt;
import ua.lab3.common.EventEnvelope;
import ua.lab3.common.EventPublisher;
import ua.lab3.common.Subjects;

/** Every window: build and publish a vector for each node of this shard. */
@Component
public class AggregationJob {
    private static final Logger log = LoggerFactory.getLogger(AggregationJob.class);

    private final NodeCache nodes;
    private final FeatureBuilder builder;
    private final EventPublisher events;
    private final int windowS;

    public AggregationJob(NodeCache nodes, FeatureBuilder builder, EventPublisher events,
                          @Value("${window.seconds}") int windowS) {
        this.nodes = nodes;
        this.builder = builder;
        this.events = events;
        this.windowS = windowS;
    }

    @Scheduled(fixedRateString = "${window.seconds}", timeUnit = TimeUnit.SECONDS)
    void tick() {
        long end = Instant.now().getEpochSecond() / windowS * windowS;
        run(Instant.ofEpochSecond(end - windowS));
    }

    void run(Instant windowStart) {
        for (Map.Entry<String, Map<String, String>> node : nodes.snapshot().entrySet()) {
            try {
                FeaturesBuilt f = builder.build(node.getKey(), node.getValue(), windowStart, windowS);
                events.publish(Subjects.FEATURES_BUILT, EventEnvelope.of(Subjects.FEATURES_BUILT, f),
                        f.nodeId() + ":" + windowStart.getEpochSecond());
                log.info("features.built {} {} {}", f.nodeId(), windowStart, summary(f));
            } catch (Exception e) {
                log.warn("skip {} for window {}: {}", node.getKey(), windowStart, e.toString());
            }
        }
    }

    private static String summary(FeaturesBuilt f) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < f.vector().size(); i++)
            sb.append(f.featureNames().get(i)).append('=').append(String.format(Locale.ROOT, "%.2f ", f.vector().get(i)));
        return sb.toString().trim();
    }
}
