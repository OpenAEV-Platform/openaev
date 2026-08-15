package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Store for autonomous runs. {@code autonomous_runs} is NOT yet onboarded to the tenant statement
 * inspector (tracked in issue #7396), so derived queries here are not tenant-scoped on their own;
 * the queries that must be tenant-correct today carry an explicit {@code tenant_id} predicate, and
 * resource-level access is gated by {@code AutonomousRunAccessControl}.
 */
@Repository
public interface AutonomousRunRepository extends JpaRepository<AutonomousRun, String> {

  Optional<AutonomousRun> findBySimulationId(String simulationId);

  Optional<AutonomousRun> findByScenarioId(String scenarioId);

  boolean existsBySimulationId(String simulationId);

  List<AutonomousRun> findAllByOrderByCreatedAtDesc();

  /**
   * Tenant-scoped primary-key lookup. Hibernate tenant {@code @Filter}s do not apply to {@code
   * find()} by primary key, so a bare {@code findById} proves nothing about scoping; paths that
   * already hold the owning tenant id (the reconciliation writer, the timeout watchdog) read
   * through this instead, keeping the read consistent with their tenant-predicated writes.
   */
  @Query("SELECT r FROM AutonomousRun r WHERE r.id = :id AND r.tenant.id = :tenantId")
  Optional<AutonomousRun> findByIdAndTenantId(
      @Param("id") String id, @Param("tenantId") String tenantId);

  /**
   * Pessimistically locked lookup ({@code SELECT ... FOR UPDATE}) for operator lifecycle/steering
   * actions (pause / resume / steer). The run row carries no optimistic version, so a plain
   * read-check-save can silently overwrite a terminal status a concurrent writer (the read-path
   * reconcile's or the watchdog's conditional terminal UPDATE, both row-locking) commits between
   * the read and the save. Taking the row lock at the read serialises the whole check-then-act with
   * those writers: if the terminal settle commits first, this read observes it and the
   * terminal-state guard rejects the action; if this action wins the lock, the conditional UPDATE
   * waits and then re-evaluates its status predicate against the committed row.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM AutonomousRun r WHERE r.id = :id")
  Optional<AutonomousRun> findByIdForUpdate(@Param("id") String id);

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
   * Ids of the given tenant's actively-working live runs the idle/stall watchdog must inspect:
   * RUNNING and NOT plan mode. WAITING_INPUT (an operator HITL park) and PAUSED are deliberate,
   * open-ended parks and are excluded; plan builds are bounded on the XTM One side and never await
   * a finding, so they are out of scope for this watchdog. Only projects the id so the sweep can
   * re-load and judge each run inside its own scoped transaction. The explicit {@code tenant_id}
   * predicate keeps a per-tenant sweep correct whether or not {@code autonomous_runs} is onboarded
   * to the tenant statement inspector.
   */
  @Query(
      "SELECT r.id FROM AutonomousRun r WHERE r.tenant.id = :tenantId "
          + "AND r.status = io.openaev.database.model.autonomous.AutonomousRunStatus.RUNNING "
          + "AND r.planMode = false")
  List<String> findLiveRunIdsForStallCheck(@Param("tenantId") String tenantId);

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

  /**
   * Watchdog variant of {@link #settleTerminalStatusIfActive}: atomically settles a run to a
   * terminal status ONLY while it is still in one of the two live statuses the deadline sweep acts
   * on (RUNNING / WAITING_INPUT). The timeout decision is made on a row read earlier in the
   * watchdog's transaction and this flip may land after a concurrent transition committed, so the
   * UPDATE re-asserts the statuses the decision was based on: an operator restart (which resets the
   * run to CREATED around a fresh simulation) or a pause must not be flipped to CANCELED by a stale
   * deadline claim. Returns 1 when this call performed the flip, 0 when the run moved on (settled,
   * restarted, paused), belongs to another tenant, or no longer exists.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE AutonomousRun r SET r.status = :target, r.updatedAt = :now "
          + "WHERE r.id = :id AND r.tenant.id = :tenantId "
          + "AND (r.status = io.openaev.database.model.autonomous.AutonomousRunStatus.RUNNING "
          + "OR r.status = io.openaev.database.model.autonomous.AutonomousRunStatus.WAITING_INPUT)")
  int settleTerminalStatusIfLive(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("target") AutonomousRunStatus target,
      @Param("now") Instant now);

  /**
   * Stall-watchdog variant: atomically settles a run to a terminal status ONLY while it is still
   * RUNNING. Unlike {@link #settleTerminalStatusIfLive}, WAITING_INPUT is deliberately NOT settled
   * - an operator HITL park is an open-ended, legitimate wait, and the idle sweep decides a run is
   * stalled on a stale-liveness read taken earlier in its transaction; if the orchestrator parked
   * the run for input between that read and this flip, re-asserting RUNNING lets the UPDATE match
   * zero rows and leaves the park intact. Returns 1 when this call performed the flip, 0 when the
   * run moved on (settled, restarted, paused, or parked for input), belongs to another tenant, or
   * no longer exists.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE AutonomousRun r SET r.status = :target, r.updatedAt = :now "
          + "WHERE r.id = :id AND r.tenant.id = :tenantId "
          + "AND r.status = io.openaev.database.model.autonomous.AutonomousRunStatus.RUNNING")
  int settleTerminalStatusIfRunning(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("target") AutonomousRunStatus target,
      @Param("now") Instant now);
}
