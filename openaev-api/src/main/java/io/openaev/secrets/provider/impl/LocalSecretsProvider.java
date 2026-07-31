package io.openaev.secrets.provider.impl;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.UsernamePasswordSecret;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.SecretsProviderType;
import io.openaev.secrets.service.SecretReferenceService;
import io.openaev.secrets.service.SecretService;
import io.openaev.service.connector_instances.NativeEncryptionService;
import java.util.Objects;

public class LocalSecretsProvider extends SecretsProvider {

  private final NativeEncryptionService nativeEncryptionService;
  private final SecretService secretService;
  private final SecretReferenceService secretReferenceService;

  public LocalSecretsProvider(
      String id,
      String name,
      NativeEncryptionService nativeEncryptionService,
      SecretService secretService,
      SecretReferenceService secretReferenceService) {
    super(id, name, SecretsProviderType.LOCAL.type);
    this.nativeEncryptionService = nativeEncryptionService;
    this.secretService = secretService;
    this.secretReferenceService = secretReferenceService;
  }

  @Override
  public SecretMetadata getSecretMetada(SecretReference secretReference) {
    SecretReference reference =
        Objects.requireNonNull(secretReference, "secretReference must not be null");
    String secretId =
        Objects.requireNonNull(
            reference.getLocation(), "secretReference location must not be null");

    Secret secret = secretService.findByIdOrThrow(secretId);
    return switch (secret) {
      case UsernamePasswordSecret usernamePasswordSecret ->
          new SecretMetadata(usernamePasswordSecret.getUsername(), null);
      case HashSecret hashSecret -> new SecretMetadata(null, hashSecret.getHashAlgorithm());
      default ->
          throw new IllegalArgumentException(
              "Unsupported secret type for main information: " + secret.getClass().getSimpleName());
    };
  }

  @Override
  public SecretReference store(SecretReference secretReference, SecretStoreRequest request) {
    SecretReference reference =
        Objects.requireNonNull(secretReference, "secretReference must not be null");

    Secret secret = buildOrUpdateSecret(getAuthMethod(reference), null, request);
    secret.setTenant(reference.getTenant());
    return persistSecretAndReference(reference, secret);
  }

  @Override
  public SecretReference update(SecretReference secretReference, SecretStoreRequest request) {
    String secretId =
        Objects.requireNonNull(
            secretReference.getLocation(), "secretReference location must not be null");

    Secret existingSecret = secretService.findByIdOrThrow(secretId);
    Secret secret = buildOrUpdateSecret(getAuthMethod(secretReference), existingSecret, request);
    return persistSecretAndReference(secretReference, secret);
  }

  private SecretReference persistSecretAndReference(SecretReference reference, Secret secret) {
    Secret persistedSecret = secretService.save(secret);
    reference.setLocation(persistedSecret.getId());
    return secretReferenceService.save(reference);
  }

  private Secret buildOrUpdateSecret(
      CredentialSecretReference.CREDENTIAL_AUTH_METHOD authMethod,
      Secret existingSecret,
      SecretStoreRequest request) {
    return switch (authMethod) {
      case HASH -> buildOrUpdateHashSecret(existingSecret, request);
      case USERNAME_PASSWORD -> buildOrUpdateUsernamePasswordSecret(existingSecret, request);
    };
  }

  private HashSecret buildOrUpdateHashSecret(Secret existingSecret, SecretStoreRequest request) {
    HashSecret hashSecret =
        existingSecret == null ? new HashSecret() : expectHashSecret(existingSecret);

    if (request.hash() != null) {
      hashSecret.setHash(encryptRequired(request.hash(), "request.hash must not be null"));
    } else if (existingSecret == null) {
      throw new IllegalArgumentException("request.hash must not be null");
    }

    if (request.hashAlgorithm() != null) {
      hashSecret.setHashAlgorithm(request.hashAlgorithm());
    } else if (existingSecret == null) {
      throw new IllegalArgumentException("request.hashAlgorithm must not be null");
    }
    return hashSecret;
  }

  private UsernamePasswordSecret buildOrUpdateUsernamePasswordSecret(
      Secret existingSecret, SecretStoreRequest request) {
    UsernamePasswordSecret passwordSecret =
        existingSecret == null
            ? new UsernamePasswordSecret()
            : expectUsernamePasswordSecret(existingSecret);

    if (request.username() != null) {
      passwordSecret.setUsername(request.username());
    } else if (existingSecret == null) {
      throw new IllegalArgumentException("request.username must not be null");
    }

    if (request.password() != null) {
      passwordSecret.setPassword(
          encryptRequired(request.password(), "request.password must not be null"));
    } else if (existingSecret == null) {
      throw new IllegalArgumentException("request.password must not be null");
    }
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

  private CredentialSecretReference.CREDENTIAL_AUTH_METHOD getAuthMethod(
      SecretReference reference) {
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
    secretService.deleteById(secretId);
    secretReferenceService.delete(reference);
  }
}
