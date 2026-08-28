package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;

public interface SecretHandler {

  boolean supports(Secret secret);

  default boolean supports(SecretReference reference) {
    return false;
  }

  Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request);

  SecretMetadata toMetadata(Secret secret);

  /**
   * Checks the credential against its provider to tell whether it is still usable.
   *
   * <p>Opt-in by design: the default answers {@link SecretConnectionResult#unsupported()}, so a
   * handler with no remote counterpart to call (local hashes, username/password pairs) needs no
   * change, and a cloud handler only implements this once its provider SDK is wired.
   *
   * <p>Implementations run OUTSIDE any transaction, on the background validation job's thread, and
   * perform network I/O. They must be self-bounded (own timeout) and must never let a transient
   * failure surface as {@code INACTIVE} — see {@link SecretConnectionResult#inactive(String)}.
   *
   * @param secret the secret to check
   * @return the validation outcome, never null
   */
  default SecretConnectionResult validateConnection(Secret secret) {
    return SecretConnectionResult.unsupported();
  }
}
