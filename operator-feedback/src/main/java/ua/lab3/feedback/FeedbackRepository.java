package ua.lab3.feedback;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, String> {
    boolean existsByAnalysisId(String analysisId);

    List<FeedbackEntity> findByCreatedAtBetween(Instant from, Instant to);

    List<FeedbackEntity> findByNodeIdAndCreatedAtAfter(String nodeId, Instant since);
}
