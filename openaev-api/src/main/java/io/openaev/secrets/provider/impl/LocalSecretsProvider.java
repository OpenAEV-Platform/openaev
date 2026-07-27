package io.openaev.secrets.provider.impl;

import static io.openaev.database.model.Secret.SECRET_TYPE.HASH_VALUE;
import static io.openaev.database.model.Secret.SECRET_TYPE.USERNAME_PASSWORD_VALUE;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.UsernamePasswordSecret;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.database.repository.SecretsRepository;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.SecretsProviderType;
import io.openaev.service.connector_instances.NativeEncryptionService;
import java.util.Objects;

public class LocalSecretsProvider extends SecretsProvider {

  private final NativeEncryptionService nativeEncryptionService;
  private final SecretsRepository secretsRepository;
  private final SecretReferenceRepository secretReferenceRepository;

  public LocalSecretsProvider(
      String id,
      String name,
      NativeEncryptionService nativeEncryptionService,
      SecretsRepository secretsRepository,
      SecretReferenceRepository secretReferenceRepository) {
    super(id, name);
    this.nativeEncryptionService = nativeEncryptionService;
    this.secretsRepository = secretsRepository;
    this.secretReferenceRepository = secretReferenceRepository;
  }

  @Override
  public SecretsProviderType getProviderType() {
    return SecretsProviderType.LOCAL;
  }

  @Override
  public SecretReference store(SecretReference secretReference, SecretStoreRequest request) {
    SecretReference reference =
        Objects.requireNonNull(secretReference, "secretReference must not be null");
    SecretStoreRequest payload = Objects.requireNonNull(request, "request must not be null");

    Secret secret = buildOrUpdateSecret(getAuthMethod(reference), null, payload);
    secret.setTenant(reference.getTenant());
    return persistSecretAndReference(reference, secret);
  }

  @Override
  public SecretReference update(SecretReference secretReference, SecretStoreRequest request) {
    SecretReference reference =
        Objects.requireNonNull(secretReference, "secretReference must not be null");
    SecretStoreRequest payload = Objects.requireNonNull(request, "request must not be null");
    String secretId =
        Objects.requireNonNull(
            reference.getLocation(), "secretReference location must not be null");

    Secret existingSecret =
        secretsRepository
            .findById(secretId)
            .orElseThrow(
                () -> new IllegalArgumentException("Secret not found for id: " + secretId));
    Secret secret = buildOrUpdateSecret(getAuthMethod(reference), existingSecret, payload);
    return persistSecretAndReference(reference, secret);
  }

  private SecretReference persistSecretAndReference(SecretReference reference, Secret secret) {
    Secret persistedSecret = secretsRepository.save(secret);
    reference.setLocation(persistedSecret.getId());
    return secretReferenceRepository.save(reference);
  }

  private Secret buildOrUpdateSecret(
      String authMethod, Secret existingSecret, SecretStoreRequest payload) {
    return switch (authMethod) {
      case HASH_VALUE -> buildOrUpdateHashSecret(existingSecret, payload);
      case USERNAME_PASSWORD_VALUE -> buildOrUpdateUsernamePasswordSecret(existingSecret, payload);
      default ->
          throw new IllegalArgumentException("Unsupported credential auth method: " + authMethod);
    };
  }

  private HashSecret buildOrUpdateHashSecret(Secret existingSecret, SecretStoreRequest payload) {
    HashSecret hashSecret =
        existingSecret == null ? new HashSecret() : expectHashSecret(existingSecret);
    hashSecret.setHash(encryptRequired(payload.hash(), "request.hash must not be null"));
    hashSecret.setHashAlgorithm(
        Objects.requireNonNull(payload.hashAlgorithm(), "request.hashAlgorithm must not be null"));
    return hashSecret;
  }

  private UsernamePasswordSecret buildOrUpdateUsernamePasswordSecret(
      Secret existingSecret, SecretStoreRequest payload) {
    UsernamePasswordSecret passwordSecret =
        existingSecret == null
            ? new UsernamePasswordSecret()
            : expectUsernamePasswordSecret(existingSecret);
    passwordSecret.setUsername(
        Objects.requireNonNull(payload.username(), "request.username must not be null"));
    passwordSecret.setPassword(
        encryptRequired(payload.password(), "request.password must not be null"));
    return passwordSecret;
  }

  private HashSecret expectHashSecret(Secret secret) {
    if (secret instanceof HashSecret hashSecret) {
      return hashSecret;
    }
    throw new IllegalArgumentException("Secret type mismatch: expected HASH secret");
  }

  private UsernamePasswordSecret expectUsernamePasswordSecret(Secret secret) {
    if (secret instanceof UsernamePasswordSecret usernamePasswordSecret) {
      return usernamePasswordSecret;
    }
    throw new IllegalArgumentException("Secret type mismatch: expected USERNAME_PASSWORD secret");
  }

  private String encryptRequired(String value, String nullMessage) {
    return nativeEncryptionService.encrypt(Objects.requireNonNull(value, nullMessage));
  }

  private String getAuthMethod(SecretReference reference) {
    if (reference instanceof CredentialSecretReference credentialSecretReference) {
      return credentialSecretReference.getCredentialAuthMethod();
    }
    throw new IllegalArgumentException(
        "LocalSecretsProvider only supports CredentialSecretReference");
  }

  @Override
  public void delete(SecretReference secretReference) {
    SecretReference reference =
        Objects.requireNonNull(secretReference, "secretReference must not be null");
    String secretId =
        Objects.requireNonNull(
            reference.getLocation(), "secretReference location must not be null");
    secretsRepository.deleteById(secretId);
    secretReferenceRepository.delete(reference);
  }
}
