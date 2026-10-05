package io.openaev.scheduler.jobs;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.service.stix.SecurityCoverageHuntValidationService;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationOutcome;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationOutcome.Kind;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationRequest;
import io.openaev.service.tenants.TenantService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * Delivers the planned OpenCTI hunt validations ({@code huntValidateFromEmulation}), tenant by
 * tenant, separately from {@link SecurityCoverageJob} so a slow or unreachable OpenCTI never delays
 * a coverage push. Does nothing unless {@code openaev.security-coverage.hunt-validation.enabled}.
 *
 * <p>Every active tenant is visited, registered connector or not, so that pending validations are
 * always postponed or given up. Per tenant: the due validations are claimed in a short scoped
 * transaction (atomically, so several OpenAEV instances never send the same validation), sent to
 * OpenCTI with no transaction open, and their outcomes recorded in a second short scoped
 * transaction. A tenant that fails is logged in one line and never stops the others.
 *
 * <p>Tenants are visited in a random order and each starts calls for at most {@code
 * SecurityCoverageHuntValidationService.TENANT_SEND_BUDGET} per run, so a slow OpenCTI never
 * starves the tenants visited after it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class SecurityCoverageHuntValidationJob implements Job {

  public static final String SECURITY_COVERAGE_HUNT_VALIDATION_JOB =
      "SecurityCoverageHuntValidationJob";
  public static final String SECURITY_COVERAGE_HUNT_VALIDATION_TRIGGER =
      "securityCoverageHuntValidationTrigger";

  private static final Set<Kind> ACTIONABLE_FAILURES =
      EnumSet.of(Kind.REFUSED, Kind.UNREACHABLE, Kind.INTERNAL_ERROR);

  private final SecurityCoverageHuntValidationService huntValidationService;
  private final TenantScopedTransaction tenantTx;
  private final TenantService tenantService;

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    if (!huntValidationService.isEnabled()) {
      return;
    }
    List<String> tenantIds = new ArrayList<>(tenantService.findActiveTenantIds());
    Collections.shuffle(tenantIds);
    for (String tenantId : tenantIds) {
      try {
        deliverForTenant(tenantId);
      } catch (Exception e) {
        log.warn(
            "OpenCTI hunt validations not delivered for tenant {}: {}", tenantId, e.getMessage());
      }
    }
  }

  private void deliverForTenant(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      List<HuntValidationRequest> due =
          tenantTx.execute(
              TxCtx.forTenant(tenantId),
              () -> huntValidationService.collectDueRequests(Instant.now()));
      if (due.isEmpty()) {
        return;
      }
      List<HuntValidationOutcome> outcomes = huntValidationService.send(tenantId, due);
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> huntValidationService.recordOutcomes(outcomes, Instant.now()));
      logSummary(tenantId, due.size(), outcomes);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private static void logSummary(
      String tenantId, int dueCount, List<HuntValidationOutcome> outcomes) {
    long validated = outcomes.stream().filter(HuntValidationOutcome::validated).count();
    List<HuntValidationOutcome> failures =
        outcomes.stream().filter(outcome -> !outcome.validated()).toList();
    if (failures.isEmpty()) {
      log.info("Sent {} OpenCTI hunt validation(s) for tenant {}", validated, tenantId);
      return;
    }
    long refused = failures.stream().filter(outcome -> outcome.kind() == Kind.REFUSED).count();
    long expired = failures.stream().filter(outcome -> outcome.kind() == Kind.EXPIRED).count();
    log.warn(
        "OpenCTI hunt validations for tenant {}: {} sent, {} refused, {} given up as stale, {}"
            + " postponed (OpenCTI unreachable, an internal error or not tried this run); last"
            + " error: {}",
        tenantId,
        validated,
        refused,
        expired,
        dueCount - validated - refused - expired,
        lastError(failures));
  }

  /**
   * The error of the latest failure that says something about the delivery (refused, unreachable or
   * internal error), so that requests deferred after it do not hide it behind their "not tried"
   * reason; the error of the last failure otherwise.
   */
  static String lastError(List<HuntValidationOutcome> failures) {
    return failures.reversed().stream()
        .filter(outcome -> ACTIONABLE_FAILURES.contains(outcome.kind()))
        .findFirst()
        .orElse(failures.getLast())
        .error();
  }
}
