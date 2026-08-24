package io.openaev.secrets.provider.impl;

import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.SecretsProviderType;
import io.openaev.secrets.provider.impl.handlers.SecretHandler;
import io.openaev.secrets.provider.impl.handlers.SecretHandlerResolver;
import io.openaev.secrets.service.SecretReferenceService;
import io.openaev.secrets.service.SecretService;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class LocalSecretsProvider extends SecretsProvider {

  private final SecretService secretService;
  private final SecretReferenceService secretReferenceService;
  private final SecretHandlerResolver secretHandlerResolver;

  public LocalSecretsProvider(
      String id,
      String name,
      SecretService secretService,
      SecretReferenceService secretReferenceService,
      SecretHandlerResolver secretHandlerResolver) {
    super(id, name, SecretsProviderType.LOCAL.type);
    this.secretService = secretService;
    this.secretReferenceService = secretReferenceService;
    this.secretHandlerResolver = secretHandlerResolver;
  }

  @Override
  public SecretMetadata getSecretMetadata(@NotNull SecretReference secretReference) {
    String secretId =
        Objects.requireNonNull(
            secretReference.getLocation(), "secretReference location must not be null");

    Secret secret = secretService.findByIdOrThrow(secretId);
    return secretHandlerResolver.resolveFor(secret).toMetadata(secret);
  }

  @Override
  public SecretReference store(
      @NotNull SecretReference secretReference, @NotNull SecretStoreRequest request) {
    SecretHandler handler = secretHandlerResolver.resolveFor(secretReference);
    Secret secret = handler.buildOrUpdate(null, request);
    secret.setTenant(secretReference.getTenant());
    return persistSecretAndReference(secretReference, secret);
  }

  @Override
  public SecretReference update(
      @NotNull SecretReference secretReference, @NotNull SecretStoreRequest request) {
    String secretId =
        Objects.requireNonNull(
            secretReference.getLocation(), "secretReference location must not be null");

    Secret existingSecret = secretService.findByIdOrThrow(secretId);
    SecretHandler handler = secretHandlerResolver.resolveFor(secretReference);
    boolean replacingSecretType = !handler.supports(existingSecret);

    Secret secret = handler.buildOrUpdate(replacingSecretType ? null : existingSecret, request);
    secret.setTenant(secretReference.getTenant());

    SecretReference persistedReference = persistSecretAndReference(secretReference, secret);
    if (replacingSecretType) {
      secretService.deleteById(existingSecret.getId());
    }
    return persistedReference;
  }

  private SecretReference persistSecretAndReference(SecretReference reference, Secret secret) {
    Secret persistedSecret = secretService.save(secret);
    reference.setLocation(persistedSecret.getId());
    return secretReferenceService.save(reference);
  }

  @Override
  public void delete(@NotNull SecretReference secretReference) {
    String secretId =
        Objects.requireNonNull(
            secretReference.getLocation(), "secretReference location must not be null");
    secretService.deleteById(secretId);
    secretReferenceService.delete(secretReference);
  }
}
