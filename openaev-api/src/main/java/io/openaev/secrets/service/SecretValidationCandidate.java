package io.openaev.secrets.service;

import io.openaev.secrets.provider.SecretConnectionProbe;
import java.util.Objects;

/**
 * One unit of work for the credential status validation run.
 *
 * <p>Carries no provider-specific state on purpose. The reference's provider is resolved during the
 * job's first, transactional phase and hands back a {@link SecretConnectionProbe} that already
 * closes over everything the check needs — a stored secret and its handler for the local backend, a
 * path and a client for a remote one. This is what keeps the DB connection out of the remote calls,
 * and what lets a new backend join the run without touching this record.
 *
 * @param referenceId the {@code secret_reference_id}, the only thing the persistence phase needs
 * @param probe the prepared check, runnable with no transaction and no session
 */
public record SecretValidationCandidate(String referenceId, SecretConnectionProbe probe) {

  public SecretValidationCandidate {
    Objects.requireNonNull(referenceId, "referenceId must not be null");
    Objects.requireNonNull(probe, "probe must not be null");
  }
}
