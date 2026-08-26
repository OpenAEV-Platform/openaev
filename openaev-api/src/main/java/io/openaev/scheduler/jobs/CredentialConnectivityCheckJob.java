package io.openaev.scheduler.jobs;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.service.SecretValidationCandidate;
import io.openaev.secrets.service.SecretValidationService;
import io.openaev.service.tenants.TenantService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Re-checks stored credentials against their provider and records the outcome, so the UI can warn
 * an operator before a simulation fails on a revoked credential.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class CredentialConnectivityCheckJob implements Job {

  public static final String CREDENTIALS_STATUS_VALIDATOR_JOB = "credentialsStatusValidatorJob";
  public static final String CREDENTIALS_STATUS_VALIDATOR_TRIGGER =
      "credentialsStatusValidatorTrigger";

  private final TenantService tenantService;
  private final TenantScopedTransaction tenantTx;
  private final SecretValidationService secretValidationService;

  @Value("${openaev.credentials.status-validation.enabled:true}")
  private boolean enabled;

  @Value("${openaev.credentials.status-validation.revalidate-after-days:7}")
  private int revalidateAfterDays;

  @Value("${openaev.credentials.status-validation.max-per-run:500}")
  private int maxPerRun;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    if (!enabled) {
      return;
    }
    Duration revalidateAfter = Duration.ofDays(revalidateAfterDays);

    // Read outside any tenant scope: `tenants` carries no tenant_id and is never rewritten by the
    // inspector, so there is no chicken-and-egg between listing tenants and scoping to one.
    List<String> tenantIds = tenantService.findActiveTenantIds();

    int failedTenants = 0;
    for (String tenantId : tenantIds) {
      try {
        validateTenantCredentials(tenantId, revalidateAfter);
      } catch (RuntimeException e) {
        // Each tenant owns its transactions, so a failure here is already rolled back and cannot
        // poison the next one. Logged and skipped rather than rethrown: one unreachable provider
        // must not stop every other tenant from being verified.
        failedTenants++;
        log.warn("Credential status validation failed for tenant {}, continuing", tenantId, e);
      }
    }
    if (failedTenants > 0) {
      log.warn(
          "Credential status validation: {} of {} tenant(s) failed",
          failedTenants,
          tenantIds.size());
    }
  }

  private void validateTenantCredentials(String tenantId, Duration revalidateAfter) {
    // Phase 1 — transactional read, and provider-side preparation of every probe.
    List<SecretValidationCandidate> candidates =
        tenantTx.execute(
            TxCtx.forTenant(tenantId),
            () ->
                secretValidationService.findDueForValidation(tenantId, maxPerRun, revalidateAfter));

    if (candidates.isEmpty()) {
      return;
    }

    // Phase 2 — network calls, no transaction and no DB connection held.
    Map<String, SecretConnectionResult> results = new LinkedHashMap<>();
    for (SecretValidationCandidate candidate : candidates) {
      results.put(candidate.referenceId(), secretValidationService.validate(candidate));
    }

    // Phase 3 — transactional write, in a fresh scoped transaction.
    int updated =
        tenantTx.execute(
            TxCtx.forTenant(tenantId), () -> secretValidationService.persistResults(results));

    log.info(
        "Credential status validation: checked {} credential(s), updated {}, for tenant {}",
        candidates.size(),
        updated,
        tenantId);
  }
}
