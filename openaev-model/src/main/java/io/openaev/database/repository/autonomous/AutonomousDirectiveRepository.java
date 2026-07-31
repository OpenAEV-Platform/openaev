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
public interface AutonomousDirectiveRepository
    extends JpaRepository<AutonomousDirective, String> {

  List<AutonomousDirective> findByRunIdAndStatusOrderByCreatedAtAsc(
      String runId, AutonomousDirectiveStatus status);

  List<AutonomousDirective> findByRunIdOrderByCreatedAtAsc(String runId);

  /** Bulk-purges a run's steering directives when the run is deleted. */
  @Modifying
  @Query("DELETE FROM AutonomousDirective d WHERE d.runId = :runId")
  void deleteByRunId(@Param("runId") String runId);
}
