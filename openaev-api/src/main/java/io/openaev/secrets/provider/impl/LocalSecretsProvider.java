package io.openaev.secrets.provider.impl;

import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.SecretsProviderType;
import io.openaev.secrets.provider.impl.handlers.SecretHandler;
import io.openaev.secrets.service.SecretReferenceService;
import io.openaev.secrets.service.SecretService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class LocalSecretsProvider extends SecretsProvider {

  private final SecretService secretService;
  private final SecretReferenceService secretReferenceService;
  private final List<SecretHandler> secretHandlers;

  public LocalSecretsProvider(
      String id,
      String name,
      SecretService secretService,
      SecretReferenceService secretReferenceService,
      List<SecretHandler> secretHandlers) {
    super(id, name, SecretsProviderType.LOCAL.type);
    this.secretService = secretService;
    this.secretReferenceService = secretReferenceService;
    this.secretHandlers = secretHandlers;
  }

  @Override
  public SecretMetadata getSecretMetadata(@NotNull SecretReference secretReference) {
    String secretId =
        Objects.requireNonNull(
            secretReference.getLocation(), "secretReference location must not be null");

    Secret secret = secretService.findByIdOrThrow(secretId);
    return resolveHandlerFor(secret).toMetadata(secret);
  }

  @Override
  public SecretReference store(
      @NotNull SecretReference secretReference, @NotNull SecretStoreRequest request) {
    SecretHandler handler = resolveHandlerFor(secretReference);
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
    SecretHandler handler = resolveHandlerFor(secretReference);
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

  private SecretHandler resolveHandlerFor(SecretReference reference) {
    SecretReference nonNullReference =
        Objects.requireNonNull(reference, "secretReference must not be null");
    return resolveHandlerFor(
        nonNullReference,
        handler -> handler.supports(nonNullReference),
        "Unsupported secret reference type: ");
  }

  private SecretHandler resolveHandlerFor(Secret secret) {
    Secret nonNullSecret = Objects.requireNonNull(secret, "secret must not be null");
    return resolveHandlerFor(
        nonNullSecret, handler -> handler.supports(nonNullSecret), "Unsupported secret type: ");
  }

  private SecretHandler resolveHandlerFor(
      Object target, Predicate<SecretHandler> supportsPredicate, String unsupportedMessagePrefix) {
    return secretHandlers.stream()
        .filter(supportsPredicate)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    unsupportedMessagePrefix + target.getClass().getSimpleName()));
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
