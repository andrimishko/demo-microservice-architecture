package ua.lab3.feedback;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnapshotRepository extends JpaRepository<SnapshotEntity, String> {
    long countByNodeIdAndStateNotAndWindowStartAfter(String nodeId, String state, Instant since);

    /** Snapshots of the node in [from, to) that nobody has given feedback on yet. */
    @Query("select s from SnapshotEntity s where s.nodeId = :node and s.windowStart >= :from and s.windowStart < :to "
            + "and not exists (select 1 from FeedbackEntity f where f.analysisId = s.analysisId) order by s.windowStart")
    List<SnapshotEntity> findWithoutFeedback(@Param("node") String node, @Param("from") Instant from, @Param("to") Instant to);
}
