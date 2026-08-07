package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;

public interface SecretHandler {

  boolean supports(Secret secret);

  default boolean supports(SecretReference reference) {
    return false;
  }

  Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request);

  SecretMetadata toMetadata(Secret secret);
}
