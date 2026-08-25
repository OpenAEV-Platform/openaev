package io.openaev.secrets.service;

import static io.openaev.secrets.provider.SecretValidationDetails.*;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.SecretReference.SECRET_STATUS;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.secrets.provider.SecretValidationResult;
import io.openaev.secrets.provider.impl.handlers.SecretHandler;
import io.openaev.secrets.provider.impl.handlers.SecretHandlerResolver;
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
 * read what is due (transactional), check it against the provider (NO transaction, network I/O),
 * then persist the outcomes (transactional).
 *
 * <p>The split is the whole point: a validation run makes one remote call per credential, and
 * holding a DB connection for its duration would starve the pool. Nothing here opens a transaction
 * around a network call.
 *
 * <p>This service is tenant-agnostic. The tenant scope is set by the caller — the job — through
 * {@code TenantScopedTransaction}, and {@code secret_references} / {@code secrets} being v2
 * tenant-scoped tables, the inspector does the filtering. No method takes or reads a tenant id.
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
  private final SecretService secretService;
  private final SecretHandlerResolver secretHandlerResolver;

  // -- READ (phase 1, transactional) --

  /**
   * Reads the credentials due for verification and materializes their secrets.
   *
   * <p>The secret is loaded HERE, not in {@link #validate}, so the network phase can run fully
   * detached. A reference whose secret cannot be loaded is still returned, with a null secret, so
   * it gets an outcome instead of vanishing from the batch.
   *
   * @param maxPerRun the run budget, must be positive
   * @param revalidateAfter how long a status stays fresh
   * @return the candidates to validate, oldest verification first
   */
  @Transactional(readOnly = true)
  public List<SecretValidationCandidate> findDueForValidation(
      int maxPerRun, Duration revalidateAfter) {
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
      candidates.add(new SecretValidationCandidate(reference.getId(), loadSecret(reference)));
    }
    return candidates;
  }

  // -- VALIDATE (phase 2, NO transaction, network I/O) --

  /**
   * Checks one credential against its provider.
   *
   * <p>Runs OUTSIDE any transaction ({@link Propagation#NOT_SUPPORTED} suspends the class-level
   * one) because it performs network I/O; it touches no repository.
   *
   * <p>Defensive by contract: a missing secret, an unresolvable handler or a validator blowing up
   * all degrade to an inconclusive outcome for THIS credential. The previous status is then kept,
   * and the rest of the batch carries on.
   *
   * @param candidate the credential to check
   * @return the outcome, never null
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public SecretValidationResult validate(SecretValidationCandidate candidate) {
    Objects.requireNonNull(candidate, "candidate must not be null");

    Secret secret = candidate.secret();
    if (secret == null) {
      log.warn(
          "Credential validation: reference {} has no resolvable secret, status left untouched",
          candidate.referenceId());
      return SecretValidationResult.notChecked(SECRET_NOT_FOUND);
    }

    Optional<SecretHandler> handler = secretHandlerResolver.findFor(secret);
    if (handler.isEmpty()) {
      log.warn(
          "Credential validation: no handler supports secret type {} of reference {}",
          secret.getType(),
          candidate.referenceId());
      return SecretValidationResult.notChecked(HANDLER_NOT_FOUND);
    }

    try {
      SecretValidationResult result = handler.get().validateConnection(secret);
      return result != null ? result : SecretValidationResult.unknown(VALIDATOR_ERROR);
    } catch (RuntimeException e) {
      // Inconclusive, never INACTIVE: an unexpected validator failure says nothing about the
      // credential itself. Message only, no stack payload: provider errors embed identifiers.
      log.warn(
          "Credential validation: validator failed for reference {}: {}",
          candidate.referenceId(),
          e.getMessage());
      return SecretValidationResult.unknown(VALIDATOR_ERROR);
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
  public int persistResults(Map<String, SecretValidationResult> resultsByReferenceId) {
    Objects.requireNonNull(resultsByReferenceId, "resultsByReferenceId must not be null");
    if (resultsByReferenceId.isEmpty()) {
      return 0;
    }

    List<SecretReference> references =
        secretReferenceRepository.findAllById(resultsByReferenceId.keySet());
    Instant verifiedAt = Instant.now();
    List<SecretReference> toSave = new ArrayList<>(references.size());

    for (SecretReference reference : references) {
      SecretValidationResult result = resultsByReferenceId.get(reference.getId());
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

  private Secret loadSecret(CredentialSecretReference reference) {
    String location = reference.getLocation();
    if (location == null || location.isBlank()) {
      log.warn("Credential validation: reference {} has no location", reference.getId());
      return null;
    }
    try {
      return secretService.findByIdOrThrow(location);
    } catch (IllegalArgumentException e) {
      log.warn(
          "Credential validation: reference {} points at a missing secret {}",
          reference.getId(),
          location);
      return null;
    }
  }
}
