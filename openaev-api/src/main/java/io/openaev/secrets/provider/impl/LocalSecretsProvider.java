package io.openaev.secrets.provider.impl;

import static io.openaev.secrets.provider.SecretConnectionDetails.HANDLER_NOT_FOUND;
import static io.openaev.secrets.provider.SecretConnectionDetails.SECRET_NOT_FOUND;

import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretConnectionProbe;
import io.openaev.secrets.provider.SecretConnectionResult;
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
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

  /**
   * Loads the stored secret and its handler HERE, in the caller's transaction, so the returned
   * probe can run fully detached.
   *
   * <p>Defensive on purpose: a dangling location or a secret type no handler claims degrades to a
   * "not checked" outcome for THIS credential — the reference is left untouched and the rest of the
   * batch carries on — rather than aborting the tenant's run.
   */
  @Override
  public SecretConnectionProbe prepareConnectionCheck(@NotNull SecretReference secretReference) {
    String location = secretReference.getLocation();
    if (location == null || location.isBlank()) {
      log.warn("Credential validation: reference {} has no location", secretReference.getId());
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked(SECRET_NOT_FOUND));
    }

    Secret secret;
    try {
      secret = secretService.findByIdOrThrow(location);
    } catch (IllegalArgumentException e) {
      log.warn(
          "Credential validation: reference {} points at a missing secret {}",
          secretReference.getId(),
          location);
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked(SECRET_NOT_FOUND));
    }

    Optional<SecretHandler> handler = secretHandlerResolver.findFor(secret);
    if (handler.isEmpty()) {
      log.warn(
          "Credential validation: no handler supports secret type {} of reference {}",
          secret.getType(),
          secretReference.getId());
      return SecretConnectionProbe.of(SecretConnectionResult.notChecked(HANDLER_NOT_FOUND));
    }

    // Both captured by value: the probe never goes back to the session.
    SecretHandler resolvedHandler = handler.get();
    return () -> resolvedHandler.validateConnection(secret);
  }
}
