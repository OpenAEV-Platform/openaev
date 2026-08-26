package io.openaev.secrets.service;

import static io.openaev.secrets.provider.SecretConnectionDetails.*;

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
          CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY);

  /**
   * Oldest first, id as tie-breaker. Without the id the order of equal {@code lastVerifiedAt} rows
   * is undefined, and a run capped by {@code maxPerRun} could keep re-checking the same subset
   * while others starve. Never-verified references come first.
   */
  private static final Sort DUE_ORDER =
      Sort.by(Sort.Order.asc("lastVerifiedAt").nullsFirst(), Sort.Order.asc("id"));

  private final SecretReferenceRepository secretReferenceRepository;
  private final SecretsProviderResolver secretsProviderResolver;

  // -- PREPARE (phase 1, transactional) --

  /**
   * Reads the credentials due for verification and asks their provider to prepare a probe.
   *
   * <p>The preparation happens HERE, not in {@link #validate}, so the network phase can run fully
   * detached. A reference whose provider cannot be resolved is still returned, with an already
   * concluded probe, so it gets an outcome instead of vanishing from the batch.
   *
   * @param tenantId the tenant owning the references, used to resolve their providers
   * @param maxPerRun the run budget, must be positive
   * @param revalidateAfter how long a status stays fresh
   * @return the candidates to validate, oldest verification first
   */
  @Transactional(readOnly = true)
  public List<SecretValidationCandidate> findDueForValidation(
      String tenantId, int maxPerRun, Duration revalidateAfter) {
    if (maxPerRun <= 0) {
      return List.of();
    }
    Instant threshold =
        Instant.now().minus(Objects.requireNonNull(revalidateAfter, "revalidateAfter is required"));
    Pageable budget = PageRequest.of(0, maxPerRun, DUE_ORDER);

    List<CredentialSecretReference> dueReferences =
        secretReferenceRepository.findDueForValidation(VALIDATABLE_AUTH_METHODS, threshold, budget);

    List<SecretValidationCandidate> candidates = new ArrayList<>(dueReferences.size());
    for (CredentialSecretReference reference : dueReferences) {
      candidates.add(
          new SecretValidationCandidate(reference.getId(), prepareProbe(tenantId, reference)));
    }
    return candidates;
  }

  /**
   * Asks the reference's provider for a probe, degrading to a concluded one on any failure.
   *
   * <p>A provider that cannot be resolved, or that blows up while preparing, must cost this ONE
   * credential an outcome — never the whole tenant batch. Both cases are "not checked": no probe
   * ever ran, so the reference is left completely untouched rather than stamped as verified.
   */
  private SecretConnectionProbe prepareProbe(String tenantId, CredentialSecretReference reference) {
    Optional<SecretsProvider> provider =
        secretsProviderResolver.findByConnectorInstanceId(
            tenantId, reference.getConnectorInstanceId());
    if (provider.isEmpty()) {
      log.warn(
          "Credential validation: no provider for reference {} (connector instance {})",
          reference.getId(),
          reference.getConnectorInstanceId());
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked(PROVIDER_NOT_FOUND));
    }
    try {
      SecretConnectionProbe probe = provider.get().prepareConnectionCheck(reference);
      return probe != null
          ? probe
          : SecretConnectionProbe.of(SecretConnectionResult.notChecked(VALIDATOR_ERROR));
    } catch (RuntimeException e) {
      // Message only, no stack payload: provider errors embed identifiers.
      log.warn(
          "Credential validation: provider failed to prepare a check for reference {}: {}",
          reference.getId(),
          e.getMessage());
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked(VALIDATOR_ERROR));
    }
  }

  // -- VALIDATE (phase 2, NO transaction, network I/O) --

  /**
   * Runs one prepared probe.
   *
   * <p>Runs OUTSIDE any transaction ({@link Propagation#NOT_SUPPORTED} suspends the class-level
   * one) because it performs network I/O; it touches no repository.
   *
   * <p>Defensive by contract: a probe blowing up or returning nothing degrades to an inconclusive
   * outcome for THIS credential. The previous status is then kept, and the rest of the batch
   * carries on.
   *
   * @param candidate the credential to check
   * @return the outcome, never null
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SecretConnectionResult validate(SecretValidationCandidate candidate) {
    Objects.requireNonNull(candidate, "candidate must not be null");
    try {
      SecretConnectionResult result = candidate.probe().run();
      return result != null ? result : SecretConnectionResult.unknown(VALIDATOR_ERROR);
    } catch (RuntimeException e) {
      // Inconclusive, never INACTIVE: an unexpected validator failure says nothing about the
      // credential itself. Message only, no stack payload: provider errors embed identifiers.
      log.warn(
          "Credential validation: validator failed for reference {}: {}",
          candidate.referenceId(),
          e.getMessage());
      return SecretConnectionResult.unknown(VALIDATOR_ERROR);
    }
  }

  // -- PERSIST (phase 3, transactional) --

  /**
   * Writes the outcomes back onto the references.
   *
   * <p>{@code lastVerifiedAt} is stamped for every credential a validator actually ran on, so a
   * permanently unreachable provider does not pin the same rows at the head of every run. The
   * status is written only on a definitive answer: an inconclusive outcome keeps the previous one.
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
