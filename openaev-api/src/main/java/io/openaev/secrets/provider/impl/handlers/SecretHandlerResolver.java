package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single place resolving which {@link SecretHandler} owns a given secret or secret reference.
 *
 * <p>Extracted out of {@code LocalSecretsProvider}, which kept it private: the background
 * credential-status validation needs the exact same resolution, and a second copy would drift the
 * day a handler is added. Handlers are discovered by Spring, so a new one is picked up here, in the
 * provider and in the validation job at once.
 */
@Component
@RequiredArgsConstructor
public class SecretHandlerResolver {

  private final List<SecretHandler> secretHandlers;

  /**
   * Resolves the handler owning a stored secret.
   *
   * @param secret the secret to resolve a handler for
   * @return the supporting handler
   * @throws IllegalArgumentException if no handler supports the secret type
   */
  public SecretHandler resolveFor(Secret secret) {
    Secret nonNullSecret = Objects.requireNonNull(secret, "secret must not be null");
    return resolve(
        nonNullSecret, handler -> handler.supports(nonNullSecret), "Unsupported secret type: ");
  }

  /**
   * Resolves the handler owning a secret reference.
   *
   * @param reference the reference to resolve a handler for
   * @return the supporting handler
   * @throws IllegalArgumentException if no handler supports the reference type
   */
  public SecretHandler resolveFor(SecretReference reference) {
    SecretReference nonNullReference =
        Objects.requireNonNull(reference, "secretReference must not be null");
    return resolve(
        nonNullReference,
        handler -> handler.supports(nonNullReference),
        "Unsupported secret reference type: ");
  }

  /**
   * Non-throwing variant, for the background validation run: an unresolvable reference must degrade
   * to an "unknown" outcome for that one credential, not abort the whole tenant batch.
   *
   * @param secret the secret to resolve a handler for
   * @return the supporting handler, or empty when none supports it
   */
  public Optional<SecretHandler> findFor(Secret secret) {
    if (secret == null) {
      return Optional.empty();
    }
    return secretHandlers.stream().filter(handler -> handler.supports(secret)).findFirst();
  }

  private SecretHandler resolve(
      Object target, Predicate<SecretHandler> supportsPredicate, String unsupportedMessagePrefix) {
    return secretHandlers.stream()
        .filter(supportsPredicate)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    unsupportedMessagePrefix + target.getClass().getSimpleName()));
  }
}
