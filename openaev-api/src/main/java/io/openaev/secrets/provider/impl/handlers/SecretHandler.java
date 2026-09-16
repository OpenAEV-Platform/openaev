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
   * Validates that the stored secret is still usable.
   *
   * <p>Handlers opt in explicitly: the default implementation reports {@link
   * SecretConnectionResult#unsupported()} so callers can distinguish "this secret type has no
   * connectivity validator" from an actual validation failure.
   *
   * @param secret the stored secret to validate
   * @return the normalized validation result, never {@code null}
   */
  default SecretConnectionResult validateConnection(Secret secret) {
    return SecretConnectionResult.unsupported();
  }
}
