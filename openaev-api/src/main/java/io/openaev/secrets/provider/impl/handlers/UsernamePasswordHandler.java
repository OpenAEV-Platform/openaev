package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.*;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import jakarta.annotation.Nullable;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsernamePasswordHandler implements SecretHandler {

  protected final NativeEncryptionService nativeEncryptionService;

  @Override
  public boolean supports(Secret secret) {
    return secret instanceof UsernamePasswordSecret;
  }

  @Override
  public boolean supports(SecretReference reference) {
    return reference instanceof CredentialSecretReference credential
        && credential.getCredentialAuthMethod()
            == CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD;
  }

  @Override
  public Secret buildOrUpdate(@Nullable Secret existingSecret, SecretStoreRequest request) {

    UsernamePasswordSecret passwordSecret =
        existingSecret instanceof UsernamePasswordSecret casted
            ? casted
            : new UsernamePasswordSecret();

    if (request.username() != null) {
      passwordSecret.setUsername(request.username());
    }

    if (request.password() != null) {
      passwordSecret.setPassword(
          nativeEncryptionService.encrypt(
              Objects.requireNonNull(request.password(), "request.password must not be null")));
    }

    if (passwordSecret.getPassword() == null || passwordSecret.getUsername() == null) {
      throw new IllegalArgumentException("Username and password are required");
    }

    return passwordSecret;
  }

  @Override
  public SecretMetadata toMetadata(Secret secret) {
    if (secret instanceof UsernamePasswordSecret usernamePasswordSecret) {
      return SecretMetadata.forUsername(usernamePasswordSecret.getUsername());
    }
    throw new IllegalArgumentException("Secret type mismatch: expected USERNAME_PASSWORD secret");
  }
}
