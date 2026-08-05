package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  /**
   * Atomically settles a still-active run to a terminal status, but ONLY when it is not already
   * terminal. Returns the number of rows changed (1 = this call performed the flip; 0 = the run was
   * already CANCELED / COMPLETED / FAILED, belongs to another tenant, or no longer exists).
   *
   * <p>The autonomous-run reconcile fires on every read ({@code get} / {@code by-simulation} /
   * {@code by-scenario}), so a frontend poll, the second detail page, the XTM One cross-side stop
   * check and the timeout watchdog can all try to settle the same run at once. Guarding the flip in
   * the DB (the row lock serialises the concurrent UPDATEs; only the first sees a non-terminal
   * status) lets the writer emit the "Run canceled" / "Run completed" timeline event exactly once
   * instead of one per racing reader - the reported duplicate + repeated "Run canceled" spam.
   * {@code clearAutomatically} so a subsequent {@code findById} in the same transaction reads the
   * freshly-committed status rather than a stale first-level-cache copy.
   *
   * <p>The explicit {@code tenant_id} predicate keeps the write correct regardless of the tenant
   * statement inspector's coverage: Hibernate {@code @Filter}s do not apply to bulk JPQL updates,
   * so without it a cross-tenant runId could settle another tenant's row. {@code updatedAt} is set
   * explicitly because a bulk update bypasses the {@code AuditableListener} lifecycle callbacks.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE AutonomousRun r SET r.status = :target, r.updatedAt = :now "
          + "WHERE r.id = :id AND r.tenant.id = :tenantId "
          + "AND r.status <> io.openaev.database.model.autonomous.AutonomousRunStatus.CANCELED "
          + "AND r.status <> io.openaev.database.model.autonomous.AutonomousRunStatus.COMPLETED "
          + "AND r.status <> io.openaev.database.model.autonomous.AutonomousRunStatus.FAILED")
  int settleTerminalStatusIfActive(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("target") AutonomousRunStatus target,
      @Param("now") Instant now);
}
