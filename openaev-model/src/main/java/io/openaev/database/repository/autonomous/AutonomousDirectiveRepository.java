package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousDirective;
import io.openaev.database.model.autonomous.AutonomousDirectiveStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Store for operator steering directives injected into a live autonomous run. */
@Repository
public interface AutonomousDirectiveRepository extends JpaRepository<AutonomousDirective, String> {

  List<AutonomousDirective> findByRunIdAndStatusOrderByCreatedAtAsc(
      String runId, AutonomousDirectiveStatus status);

  List<AutonomousDirective> findByRunIdOrderByCreatedAtAsc(String runId);

  /**
   * Cheap existence probe: does the run still have a directive in the given status? Used by the
   * run/simulation reconciliation as a proof-of-life signal - a PENDING directive means the
   * operator just steered and the orchestrator has not consumed it yet, so the run must not be
   * settled from a stale simulation status.
   */
  boolean existsByRunIdAndStatus(String runId, AutonomousDirectiveStatus status);

  /** Bulk-purges a run's steering directives when the run is deleted. */
  @Modifying
  @Query("DELETE FROM AutonomousDirective d WHERE d.runId = :runId")
  void deleteByRunId(@Param("runId") String runId);
}
