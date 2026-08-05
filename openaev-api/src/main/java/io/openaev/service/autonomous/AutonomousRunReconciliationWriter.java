package io.openaev.service.autonomous;

import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated writer for reconciled autonomous-run status flips.
 *
 * <p>It lives in its own bean on purpose so the read-path reconcile ({@link
 * AutonomousRunService#reconcileWithSimulation}) persists the status sync in a fresh {@code
 * REQUIRES_NEW} transaction THROUGH the Spring proxy. A same-class call would bypass the proxy,
 * keep writing inside the caller's read-only transaction, and mark it rollback-only - the root
 * cause of the "UnexpectedRollbackException" 500 on Stop that made the UI fall back to the manual
 * (non-AI) view. Being a separate bean also keeps this a real cross-bean call rather than a flagged
 * transactional self-invocation.
 */
@Service
@RequiredArgsConstructor
public class AutonomousRunReconciliationWriter {

  private final AutonomousRunRepository runRepository;
  private final AutonomousEventService eventService;

  /**
   * Persists a reconciled run status flip and its STATUS timeline event in a fresh, isolated
   * transaction. Returns {@code null} when the run vanished in the meantime, so the caller can
   * degrade gracefully.
   *
   * <p>Idempotent under concurrent reconciles. The flip is a conditional, atomic UPDATE that only
   * touches a still-active run, so when several readers race to settle the same run (a frontend
   * poll on the scenario page, the simulation page, and the XTM One cross-side stop check all
   * reconcile on read) exactly one wins the DB row and records the "Run canceled" / "Run completed"
   * event; the losers stay silent. This is the fix for the duplicated + repeated "Run canceled"
   * timeline spam where every racing reader appended its own identical status event.
   *
   * <p>{@code tenantId} is the run's owning tenant, threaded from the caller's loaded entity: the
   * conditional UPDATE is a bulk JPQL statement, which Hibernate tenant {@code @Filter}s do not
   * cover, so the write carries its own explicit tenant predicate.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public AutonomousRun settleRunStatus(
      String runId, String tenantId, AutonomousRunStatus target, String reasonDetail) {
    int changed =
        runRepository.settleTerminalStatusIfActive(runId, tenantId, target, Instant.now());
    AutonomousRun run = runRepository.findById(runId).orElse(null);
    if (run == null) {
      return null;
    }
    // Only the reader that actually performed the flip narrates it: a concurrent reconcile that
    // found the run already terminal (changed == 0) must not append a second identical event.
    if (changed > 0) {
      eventService.append(
          run.getId(),
          run.getSimulationId(),
          AutonomousEventType.STATUS,
          target == AutonomousRunStatus.CANCELED ? "Run canceled" : "Run completed",
          reasonDetail,
          null);
    }
    return run;
  }
}
