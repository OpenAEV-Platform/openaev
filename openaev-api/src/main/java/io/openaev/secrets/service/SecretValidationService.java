package io.openaev.secrets.service;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.SecretReference.SECRET_STATUS;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.secrets.provider.SecretConnectionProbe;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.SecretsProviderResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives the credential status validation, split into the three phases the background job needs:
 * prepare what is due (transactional), probe it (NO transaction, network I/O), then persist the
 * outcomes (transactional).
 *
 * <p>The split is the whole point: a validation run makes one remote call per credential, and
 * holding a DB connection for its duration would starve the pool. Nothing here opens a transaction
 * around a network call.
 *
 * <p>Backend-agnostic by construction: this service never loads a secret nor resolves a handler
 * itself. It asks the {@link SecretsProvider} owning each reference to prepare a {@link
 * SecretConnectionProbe}, so a new backend joins the run without a line changing here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class SecretValidationService {

  /**
   * The auth methods a validator exists for.
   *
   * <p>Maintained by hand, deliberately: {@code SecretHandler#validateConnection} has a default
   * implementation, so "does this handler really validate?" cannot be introspected without
   * reflection tricks. An explicit constant is greppable and testable — when a new cloud validator
   * lands (GCP, AWS), adding its method here is the single switch that puts it in the run.
   */
  public static final Set<CREDENTIAL_AUTH_METHOD> VALIDATABLE_AUTH_METHODS =
      EnumSet.of(
          CREDENTIAL_AUTH_METHOD.AZURE_SERVICE_PRINCIPAL,
          CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY,
          CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY,
          CREDENTIAL_AUTH_METHOD.AWS_ASSUME_ROLE);

  /**
   * Oldest first, id as tie-breaker. Without the id the order of equal {@code lastVerifiedAt} rows
   * is undefined, and a run capped by {@code maxPerRun} could keep re-checking the same subset
   * while others starve. Never-verified references come first.
   */
  private static final Sort DUE_ORDER =
      Sort.by(Sort.Order.asc("lastVerifiedAt").nullsFirst(), Sort.Order.asc("id"));

  private final SecretReferenceRepository secretReferenceRepository;
  private final SecretsProviderResolver secretsProviderResolver;
  private final TenantScopedTransaction tenantTx;

  // -- PREPARE (phase 1, transactional) --

  @Transactional(readOnly = true)
  public List<CredentialSecretReference> findDueForValidation(
      int maxPerRun, Duration revalidateAfter) {
    if (maxPerRun <= 0) {
      return List.of();
    }
    Instant threshold =
        Instant.now().minus(Objects.requireNonNull(revalidateAfter, "revalidateAfter is required"));
    Pageable budget = PageRequest.of(0, maxPerRun, DUE_ORDER);

    return secretReferenceRepository.findDueForValidation(
        VALIDATABLE_AUTH_METHODS, threshold, budget);
  }

  // -- PROBE (phase 2, NO transaction) --
  /**
   * Asks the reference's provider for a probe, degrading to a concluded one on any failure.
   *
   * <p>A provider that cannot be resolved, or that blows up while preparing, must cost this ONE
   * credential an outcome — never the whole tenant batch. Both cases are "not checked": no probe
   * ever ran, so the reference is left completely untouched rather than stamped as verified.
   */
  private SecretConnectionProbe prepareProbe(String tenantId, CredentialSecretReference reference) {
    if (shouldSkipProbePreparation(tenantId, reference)) {
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked());
    }
    Optional<SecretsProvider> provider =
        secretsProviderResolver.findByConnectorInstanceId(
            tenantId, reference.getConnectorInstanceId());
    if (provider.isEmpty()) {
      log.warn(
          "Credential validation: no provider for reference {} (connector instance {})",
          reference.getId(),
          reference.getConnectorInstanceId());
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked());
    }
    return prepareProbe(tenantId, provider.get(), reference);
  }

  private boolean shouldSkipProbePreparation(String tenantId, CredentialSecretReference reference) {
    if (tenantId == null || tenantId.isBlank()) {
      log.warn("Credential validation: missing tenant id while preparing probe");
      return true;
    }
    if (reference == null) {
      log.warn("Credential validation: missing credential reference while preparing probe");
      return true;
    }
    CREDENTIAL_AUTH_METHOD authMethod = reference.getCredentialAuthMethod();
    if (authMethod == null || !VALIDATABLE_AUTH_METHODS.contains(authMethod)) {
      log.warn(
          "Credential validation: auth method {} is not validatable for reference {}",
          authMethod,
          reference.getId());
      return true;
    }
    return false;
  }

  private SecretConnectionProbe prepareProbe(
      String tenantId, SecretsProvider provider, CredentialSecretReference reference) {
    if (shouldSkipProbePreparation(tenantId, reference)) {
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked());
    }
    if (provider == null) {
      log.warn(
          "Credential validation: missing provider while preparing probe for reference {}",
          reference.getId());
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked());
    }

    try {
      SecretConnectionProbe probe =
          tenantTx.execute(
              TxCtx.forTenant(tenantId), () -> provider.prepareConnectionCheck(reference));
      return probe != null ? probe : SecretConnectionProbe.of(SecretConnectionResult.notChecked());
    } catch (RuntimeException e) {
      log.warn(
          "Credential validation: provider failed to prepare a check for reference {}: {}",
          reference.getId(),
          e.getMessage());
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked());
    }
  }

  /**
   * Runs one prepared probe.
   *
   * <p>Runs OUTSIDE any transaction ({@link Propagation#NOT_SUPPORTED} suspends the class-level
   * one) because it performs network I/O; it touches no repository.
   *
   * <p>Defensive by contract: a probe blowing up or returning nothing degrades to UNKNOWN for THIS
   * credential, and the rest of the batch carries on.
   *
   * @return the outcome, never null
   */
  private SecretConnectionResult runProbe(SecretConnectionProbe probe) {
    if (probe == null) {
      log.warn("Credential validation: missing probe before connectivity check");
      return SecretConnectionResult.notChecked();
    }
    try {
      SecretConnectionResult result = probe.run();
      return result != null ? result : SecretConnectionResult.notChecked();
    } catch (RuntimeException e) {
      log.warn("Credential validation: validator failed: {}", e.getMessage());
      return SecretConnectionResult.unknown();
    }
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SecretConnectionResult validateConnectivity(
      String tenantId, CredentialSecretReference secretReferenceToValidate) {
    SecretConnectionProbe probe = prepareProbe(tenantId, secretReferenceToValidate);
    return runProbe(probe);
  }

  /**
   * Validates one credential right after a write operation and persists the outcome in a fresh
   * tenant-scoped transaction.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void validateAfterWrite(
      SecretsProvider provider, CredentialSecretReference credentialReference) {
    if (credentialReference == null) {
      log.warn("Credential validation after write: missing credential reference");
      return;
    }
    String referenceId = credentialReference.getId();
    if (referenceId == null || referenceId.isBlank()) {
      return;
    }
    String tenantId =
        Objects.requireNonNull(
                credentialReference.getTenant(), "credential tenant must not be null")
            .getId();
    if (tenantId == null || tenantId.isBlank()) {
      log.warn("Credential validation after write: missing tenant for reference {}", referenceId);
      return;
    }

    try {
      SecretConnectionProbe probe = prepareProbe(tenantId, provider, credentialReference);
      SecretConnectionResult result = runProbe(probe);
      int updated =
          tenantTx.execute(
              TxCtx.forTenant(tenantId), () -> persistResults(Map.of(referenceId, result)));
      log.info(
          "Credential status validation after write: checked 1 credential, updated {}, for tenant {}",
          updated,
          tenantId);
    } catch (RuntimeException e) {
      log.warn(
          "Credential connectivity check after secret write failed for reference {} in tenant {}",
          referenceId,
          tenantId);
    }
  }

  // -- PERSIST (phase 3, transactional) --

  /**
   * Writes the outcomes back onto the references.
   *
   * <p>{@code lastVerifiedAt} is stamped for every credential a validator actually ran on, so a
   * permanently unreachable provider does not pin the same rows at the head of every run. The
   * canonical status from {@link SecretConnectionResult} is persisted whenever a check actually
   * ran.
   *
   * <p>{@code updatedAt} moves, as it does on any Hibernate update ({@code @UpdateTimestamp}); this
   * is accepted rather than worked around with a bulk update.
   *
   * @param resultsByReferenceId outcomes keyed by {@code secret_reference_id}
   * @return the number of references actually updated
   */
  public int persistResults(Map<String, SecretConnectionResult> resultsByReferenceId) {
    Objects.requireNonNull(resultsByReferenceId, "resultsByReferenceId must not be null");
    if (resultsByReferenceId.isEmpty()) {
      return 0;
    }

    List<SecretReference> references =
        secretReferenceRepository.findAllById(resultsByReferenceId.keySet());
    Instant verifiedAt = Instant.now();
    List<SecretReference> toSave = new ArrayList<>(references.size());

    for (SecretReference reference : references) {
      SecretConnectionResult result = resultsByReferenceId.get(reference.getId());
      if (result == null || !result.wasChecked()) {
        continue;
      }
      reference.setLastVerifiedAt(verifiedAt);
      Optional<SECRET_STATUS> status = result.statusToPersist();
      status.ifPresent(reference::setStatus);
      toSave.add(reference);
    }

    secretReferenceRepository.saveAll(toSave);
    return toSave.size();
  }
}
