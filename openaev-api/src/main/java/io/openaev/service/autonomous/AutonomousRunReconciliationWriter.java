package io.openaev.service.autonomous;

import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
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
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public AutonomousRun settleRunStatus(
      String runId, AutonomousRunStatus target, String reasonDetail) {
    AutonomousRun run = runRepository.findById(runId).orElse(null);
    if (run == null) {
      return null;
    }
    run.setStatus(target);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        run.getId(),
        run.getSimulationId(),
        AutonomousEventType.STATUS,
        target == AutonomousRunStatus.CANCELED ? "Run canceled" : "Run completed",
        reasonDetail,
        null);
    return saved;
  }
}
