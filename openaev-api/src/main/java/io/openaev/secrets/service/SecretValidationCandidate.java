package io.openaev.secrets.service;

import io.openaev.database.model.Secret;
import java.util.Objects;

/**
 * One unit of work for the credential status validation run.
 *
 * <p>The reference and its secret are read together during the job's first, transactional phase,
 * then carried DETACHED through the network phase. This is what keeps the DB connection out of the
 * remote calls: the validation phase must be able to run with no session at all, so everything it
 * needs is materialized here upfront.
 *
 * <p>{@code secret} is nullable on purpose: a reference with a null or dangling {@code location}
 * still travels through the run so it gets an outcome (and a warning) instead of silently
 * disappearing from the batch.
 *
 * @param referenceId the {@code secret_reference_id}, the only thing the persistence phase needs
 * @param secret the secret to check, or null when it could not be loaded
 */
public record SecretValidationCandidate(String referenceId, Secret secret) {

  public SecretValidationCandidate {
    Objects.requireNonNull(referenceId, "referenceId must not be null");
  }
}
