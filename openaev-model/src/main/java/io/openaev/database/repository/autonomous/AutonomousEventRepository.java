package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Append-only, per-run-sequenced timeline store for autonomous runs. */
@Repository
public interface AutonomousEventRepository extends JpaRepository<AutonomousEvent, String> {

  List<AutonomousEvent> findByRunIdOrderBySequenceAsc(String runId);

  List<AutonomousEvent> findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(
      String runId, long sequence);

  /** Highest sequence written for a run, 0 when the run has no events yet. */
  @Query(
      "SELECT COALESCE(MAX(e.sequence), 0) FROM AutonomousEvent e WHERE e.runId = :runId")
  long findMaxSequence(@Param("runId") String runId);
}
