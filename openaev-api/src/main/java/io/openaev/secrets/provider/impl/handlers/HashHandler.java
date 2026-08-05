package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HashHandler implements SecretHandler {

  protected final NativeEncryptionService nativeEncryptionService;

  @Override
  public boolean supports(Secret secret) {
    return secret instanceof HashSecret;
  }

  @Override
  public boolean supports(SecretReference reference) {
    return reference instanceof CredentialSecretReference credential
        && credential.getCredentialAuthMethod()
            == CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH;
  }

  @Override
  public Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request) {
    HashSecret hashSecret = existingSecret instanceof HashSecret casted ? casted : new HashSecret();

    if (request.hashAlgorithm() != null) {
      hashSecret.setHashAlgorithm(request.hashAlgorithm());
    }

    if (request.hash() != null) {
      hashSecret.setHash(
          nativeEncryptionService.encrypt(
              Objects.requireNonNull(request.hash(), "request.hash must not be null")));
    }

    if (hashSecret.getHash() == null || hashSecret.getHashAlgorithm() == null) {
      throw new IllegalArgumentException("Hash algorithm and hash are required");
    }

    return hashSecret;
  }

  @Override
  public SecretMetadata toMetadata(Secret secret) {
    if (secret instanceof HashSecret hashSecret) {
      return new SecretMetadata(null, hashSecret.getHashAlgorithm());
    }
    throw new IllegalArgumentException("Secret type mismatch: expected HASH secret");
  }
}
