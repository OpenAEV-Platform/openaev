package io.openaev.secrets.provider;

/**
 * A credential liveness check, prepared inside a transaction and executed outside of it.
 *
 * <p>This is what makes the validation run provider-agnostic without giving up its central
 * guarantee. {@code SecretsProvider#prepareConnectionCheck} runs in the job's transactional phase
 * and lets each provider materialize whatever IT needs — the local provider loads the stored {@code
 * Secret} and resolves its handler, a remote one would resolve a path and a client — then hands
 * back this closure. The job runs it with no transaction and no DB connection held.
 *
 * <p>An implementation must therefore be fully DETACHED: it must close over plain values, never
 * over a repository, a session, or a lazy Hibernate proxy. Touching the database from here would
 * defeat the whole three-phase design.
 */
@FunctionalInterface
public interface SecretConnectionProbe {

  /**
   * Runs the check. Called outside any transaction, on the background job's thread.
   *
   * @return the outcome, never null
   */
  SecretConnectionResult run();

  /**
   * A probe that has already concluded, for the cases decided at preparation time (dangling secret,
   * no handler, provider that does not support validation).
   *
   * @param result the outcome to replay
   * @return a probe yielding that outcome
   */
  static SecretConnectionProbe of(SecretConnectionResult result) {
    return () -> result;
  }
}
