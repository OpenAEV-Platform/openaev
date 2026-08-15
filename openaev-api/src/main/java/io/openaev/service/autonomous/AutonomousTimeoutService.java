package io.openaev.service.autonomous;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Server-side watchdog for autonomous runs, invoked periodically by {@code AutonomousTimeoutJob}. It
 * runs two independent per-tenant sweeps:
 *
 * <ul>
 *   <li><b>Deadline</b> - for each run whose OpenAEV-enforced deadline is near or passed, delegates
 *       to {@link AutonomousRunService#enforceDeadline(String, String)} to queue a winddown steering
 *       nudge (5 min / 1 min before) or hard-stop the run at the deadline (exactly like an operator
 *       Stop).
 *   <li><b>Liveness/stall</b> - for each live (RUNNING, non-plan) run, delegates to {@link
 *       AutonomousRunService#enforceLiveness(String, String)} to settle a run that has gone SILENT
 *       (no timeline heartbeat for {@link AutonomousRunService#STALL_IDLE_SECONDS}) with nothing in
 *       flight to justify the silence - a crashed / disconnected / unauthenticated orchestrator -
 *       long before its 24h deadline, while exempting a genuine {@code await_finding} park.
 * </ul>
 *
 * <p>This is the missing OpenAEV-side liveness guarantee: because an autonomous simulation is a
 * keep-alive chaining workflow, every native engine terminal condition (workflow END, the workflow
 * timeout job, the auto-close scheduler) is deliberately disabled for it. Without this watchdog a
 * run whose orchestrator crashes, disconnects, or never posts a terminal status would stay RUNNING
 * forever (or, before the stall sweep, until the 24h deadline).
 *
 * <p>Tenancy/transactions: the sweep is background code, so it never uses {@code @Transactional}.
 * It runs the documented per-tenant idiom - {@link TenantScopedTransaction#forEachTenant} opens one
 * scoped transaction per active tenant, and each run is enforced inside its own {@code
 * executeNew(REQUIRES_NEW)} boundary so a single failing run cannot poison the tenant's sweep. No
 * current-user / security context is required: the enforcement path performs only tenant-scoped DB
 * writes and lets the orchestrator's own next-cycle stop check tear XTM One down.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutonomousTimeoutService {

  private final AutonomousRunRepository runRepository;
  private final AutonomousRunService autonomousRunService;
  private final TenantScopedTransaction tenantTx;

  /** Sweeps every tenant's live runs and enforces the deadline policy on those that are due. */
  public void sweep() {
    // Look ahead by the widest winddown lead time so a run gets its 5-minute nudge on the same
    // sweep that first sees it inside the window.
    Instant threshold = Instant.now().plusSeconds(AutonomousRunService.WINDDOWN_5M_SECONDS);
    tenantTx.forEachTenant(
        tenantId -> {
          // v1 @Filter bridge for the tenant-filtered entities the enforcement path touches
          // (simulation / workflow); the v2 GUC scope is already set by forEachTenant.
          TenantContext.setCurrentTenant(tenantId);
          try {
            List<String> dueRunIds = runRepository.findRunIdsDueForTimeout(tenantId, threshold);
            for (String runId : dueRunIds) {
              try {
                // REQUIRES_NEW isolates each run: one bad run rolls back alone and never poisons
                // the tenant's outer sweep transaction or its siblings (see TenantScopedTransaction
                // poisoning rule).
                tenantTx.executeNew(
                    TxCtx.forTenant(tenantId),
                    () -> autonomousRunService.enforceDeadline(runId, tenantId));
              } catch (RuntimeException e) {
                log.warn("[Autonomous] Timeout enforcement failed for run {}", runId, e);
              }
            }
            // Independently of the 24h deadline, settle runs that have gone SILENT: a live run whose
            // orchestrator crashed, disconnected, or cannot authenticate stops posting timeline
            // heartbeats but never reaches its deadline, so without this it shows a frozen cockpit
            // for up to 24h. enforceLiveness exempts a genuine await_finding park (a step result is
            // still pending) and WAITING_INPUT / plan runs (excluded by the query).
            List<String> liveRunIds = runRepository.findLiveRunIdsForStallCheck(tenantId);
            for (String runId : liveRunIds) {
              try {
                tenantTx.executeNew(
                    TxCtx.forTenant(tenantId),
                    () -> autonomousRunService.enforceLiveness(runId, tenantId));
              } catch (RuntimeException e) {
                log.warn("[Autonomous] Stall enforcement failed for run {}", runId, e);
              }
            }
          } finally {
            TenantContext.clearCurrentTenant();
          }
        });
  }
}
