package io.openaev.scheduler.jobs;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.secrets.provider.SecretConnectionResult;
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

  public static final String CREDENTIAL_CONNECTIVITY_CHECK_JOB = "credentialConnectivityCheckJob";
  public static final String CREDENTIAL_CONNECTIVITY_CHECK_TRIGGER =
      "credentialConnectivityCheckTrigger";

  private final TenantService tenantService;
  private final TenantScopedTransaction tenantTx;
  private final SecretValidationService secretValidationService;

  @Value("${openaev.credentials.status-validation.enabled:true}")
  private boolean enabled;

  @Value("${openaev.credentials.status-validation.revalidate-after-days:1}")
  private int revalidateAfterDays;

  @Value("${openaev.credentials.status-validation.max-per-run:500}")
  private int maxPerRun;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    if (!enabled) {
      return;
    }
    Duration revalidateAfter = Duration.ofDays(revalidateAfterDays < 0 ? 1 : revalidateAfterDays);

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
    // Phase 1 — transactional read of credentials due for validation.
    List<CredentialSecretReference> secretReferencesToValidate =
        tenantTx.execute(
            TxCtx.forTenant(tenantId),
            () -> secretValidationService.findDueForValidation(maxPerRun, revalidateAfter));

    if (secretReferencesToValidate.isEmpty()) {
      return;
    }

    // Phase 2 — network connectivity checks.
    Map<String, SecretConnectionResult> resultsByReferenceId = new LinkedHashMap<>();
    for (CredentialSecretReference secretReference : secretReferencesToValidate) {
      resultsByReferenceId.put(
          secretReference.getId(),
          secretValidationService.validateConnectivity(tenantId, secretReference));
    }

    // Phase 3 — persist results in a fresh tenant-scoped transaction.
    int updated =
        tenantTx.execute(
            TxCtx.forTenant(tenantId),
            () -> secretValidationService.persistResults(resultsByReferenceId));

    log.info(
        "Credential status validation: checked {} credential(s), updated {}, for tenant {}",
        secretReferencesToValidate.size(),
        updated,
        tenantId);
  }
}
