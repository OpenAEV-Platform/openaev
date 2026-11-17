package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.ApiTokenSecret;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretResolvedValue;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiTokenHandler implements SecretHandler {

  protected final NativeEncryptionService nativeEncryptionService;

  @Override
  public boolean supports(Secret secret) {
    return secret instanceof ApiTokenSecret;
  }

  @Override
  public boolean supports(SecretReference reference) {
    return reference instanceof CredentialSecretReference credential
        && credential.getCredentialAuthMethod()
            == CredentialSecretReference.CREDENTIAL_AUTH_METHOD.API_TOKEN;
  }

  @Override
  public Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request) {
    ApiTokenSecret apiTokenSecret =
        existingSecret instanceof ApiTokenSecret casted ? casted : new ApiTokenSecret();
    if (request.apiToken() != null && !request.apiToken().isBlank()) {
      apiTokenSecret.setApiToken(nativeEncryptionService.encrypt(request.apiToken()));
    } else if (request.apiToken() != null || apiTokenSecret.getApiToken() == null) {
      throw new IllegalArgumentException("API token is required");
    }
    return apiTokenSecret;
  }

  @Override
  public SecretMetadata toMetadata(Secret secret) {
    if (secret instanceof ApiTokenSecret) {
      return SecretMetadata.empty();
    }
    throw new IllegalArgumentException("Secret type mismatch: expected API_TOKEN secret");
  }

  @Override
  public SecretResolvedValue toResolvedValue(Secret secret) {
    if (secret instanceof ApiTokenSecret apiTokenSecret) {
      return SecretResolvedValue.forApiToken(
          nativeEncryptionService.decrypt(apiTokenSecret.getApiToken()));
    }
    throw new IllegalArgumentException("Secret type mismatch: expected API_TOKEN secret");
  }
}
