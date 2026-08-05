package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Tenant-active store for autonomous runs. The tenant predicate on {@code autonomous_runs} is added
 * by the statement inspector, so these derived queries stay tenant-scoped without an explicit
 * clause.
 */
@Repository
public interface AutonomousRunRepository extends JpaRepository<AutonomousRun, String> {

  Optional<AutonomousRun> findBySimulationId(String simulationId);

  Optional<AutonomousRun> findByScenarioId(String scenarioId);

  boolean existsBySimulationId(String simulationId);

  List<AutonomousRun> findAllByOrderByCreatedAtDesc();

  /**
   * Ids of the given tenant's live runs whose OpenAEV-enforced deadline is at or within {@code
   * threshold} (i.e. already passed, or close enough that a winddown nudge is due). The explicit
   * {@code tenant_id} predicate keeps a per-tenant sweep correct whether or not {@code
   * autonomous_runs} is onboarded to the tenant statement inspector. Only projects the id so the
   * watchdog can re-load each run inside its own scoped transaction.
   */
  @Query(
      "SELECT r.id FROM AutonomousRun r WHERE r.tenant.id = :tenantId "
          + "AND r.deadlineAt IS NOT NULL AND r.deadlineAt <= :threshold "
          + "AND (r.status = io.openaev.database.model.autonomous.AutonomousRunStatus.RUNNING "
          + "OR r.status = io.openaev.database.model.autonomous.AutonomousRunStatus.WAITING_INPUT)")
  List<String> findRunIdsDueForTimeout(
      @Param("tenantId") String tenantId, @Param("threshold") Instant threshold);
}
