package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretResolvedValue;
import io.openaev.secrets.provider.SecretStoreRequest;

public interface SecretHandler {

  boolean supports(Secret secret);

  default boolean supports(SecretReference reference) {
    return false;
  }

  Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request);

  SecretMetadata toMetadata(Secret secret);

  SecretResolvedValue toResolvedValue(Secret secret);

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

  /**
   * Applies the "null = untouched, blank = clear" rule of the optional text fields: a blank value
   * removes the stored one, so a user can drop an override and fall back to the official endpoint.
   *
   * @param value the value of the request, null when the client left the field untouched
   * @return {@code null} for a blank value, the value itself otherwise
   */
  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
