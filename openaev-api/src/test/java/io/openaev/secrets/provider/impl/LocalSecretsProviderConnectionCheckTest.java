package io.openaev.secrets.provider.impl;

import static io.openaev.database.model.SecretReference.SECRET_STATUS.ACTIVE;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.UNSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Secret;
import io.openaev.secrets.provider.SecretConnectionProbe;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.impl.handlers.SecretHandler;
import io.openaev.secrets.provider.impl.handlers.SecretHandlerResolver;
import io.openaev.secrets.service.SecretReferenceService;
import io.openaev.secrets.service.SecretService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@code prepareConnectionCheck} side of the local provider — the backend-side
 * half of the credential connectivity check.
 */
@DisplayName("LocalSecretsProvider prepareConnectionCheck tests")
class LocalSecretsProviderConnectionCheckTest {

  private static final String SECRET_LOCATION = "secret-location-id";

  private SecretService secretService;
  private SecretHandlerResolver secretHandlerResolver;
  private LocalSecretsProvider provider;

  @BeforeEach
  void setUp() {
    secretService = mock(SecretService.class);
    secretHandlerResolver = mock(SecretHandlerResolver.class);
    provider =
        new LocalSecretsProvider(
            "local-provider-id",
            "Local",
            secretService,
            mock(SecretReferenceService.class),
            secretHandlerResolver);
  }

  private static CredentialSecretReference referenceAt(String location) {
    CredentialSecretReference reference = new CredentialSecretReference();
    reference.setId("reference-id");
    reference.setLocation(location);
    return reference;
  }

  @Nested
  @DisplayName("Preparation")
  class Preparation {

    @Test
    @DisplayName("Given a resolvable secret, should hand the handler's outcome back")
    void given_resolvableSecret_should_returnHandlerOutcome() {
      // Arrange
      Secret secret = new HashSecret();
      SecretHandler handler = mock(SecretHandler.class);
      when(secretService.findByIdOrThrow(SECRET_LOCATION)).thenReturn(secret);
      when(secretHandlerResolver.findFor(secret)).thenReturn(Optional.of(handler));
      when(handler.validateConnection(secret)).thenReturn(SecretConnectionResult.active());

      // Act
      SecretConnectionResult result =
          provider.prepareConnectionCheck(referenceAt(SECRET_LOCATION)).run();

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
      assertThat(result.wasChecked()).isTrue();
    }

    @Test
    @DisplayName("Given a reference with no location, should conclude without touching the DB")
    void given_noLocation_should_returnSecretNotFound() {
      // Act
      SecretConnectionResult result = provider.prepareConnectionCheck(referenceAt(null)).run();

      // Assert
      assertThat(result.status()).isEqualTo(UNSET);
      assertThat(result.wasChecked()).isFalse();
      verifyNoInteractions(secretService);
    }

    @Test
    @DisplayName("Given a blank location, should conclude without touching the DB")
    void given_blankLocation_should_returnSecretNotFound() {
      // Act
      SecretConnectionResult result = provider.prepareConnectionCheck(referenceAt("  ")).run();

      // Assert
      assertThat(result.status()).isEqualTo(UNSET);
      assertThat(result.wasChecked()).isFalse();
      verifyNoInteractions(secretService);
    }

    @Test
    @DisplayName("Given a dangling location, should degrade instead of propagating")
    void given_danglingLocation_should_returnSecretNotFound() {
      // Arrange
      when(secretService.findByIdOrThrow(SECRET_LOCATION))
          .thenThrow(new IllegalArgumentException("Secret not found"));

      // Act
      SecretConnectionResult result =
          provider.prepareConnectionCheck(referenceAt(SECRET_LOCATION)).run();

      // Assert — one dangling reference must not abort the tenant's batch.
      assertThat(result.status()).isEqualTo(UNSET);
      assertThat(result.wasChecked()).isFalse();
    }

    @Test
    @DisplayName("Given a secret no handler claims, should report a missing handler")
    void given_noHandler_should_returnHandlerNotFound() {
      // Arrange
      Secret secret = new HashSecret();
      when(secretService.findByIdOrThrow(SECRET_LOCATION)).thenReturn(secret);
      when(secretHandlerResolver.findFor(secret)).thenReturn(Optional.empty());

      // Act
      SecretConnectionResult result =
          provider.prepareConnectionCheck(referenceAt(SECRET_LOCATION)).run();

      // Assert
      assertThat(result.status()).isEqualTo(UNSET);
      assertThat(result.wasChecked()).isFalse();
    }
  }

  @Nested
  @DisplayName("Detachment invariant")
  class DetachmentInvariant {

    /**
     * The central guarantee of the three-phase design: the probe runs during the job's network
     * phase, with no transaction and no DB connection held. If running it went back to the
     * database, one remote call per credential would pin a pooled connection for its whole duration
     * — exactly what the split exists to prevent.
     */
    @Test
    @DisplayName("Given a prepared probe, running it should touch no repository")
    void given_preparedProbe_should_notTouchTheDatabase() {
      // Arrange
      Secret secret = new HashSecret();
      SecretHandler handler = mock(SecretHandler.class);
      when(secretService.findByIdOrThrow(SECRET_LOCATION)).thenReturn(secret);
      when(secretHandlerResolver.findFor(secret)).thenReturn(Optional.of(handler));
      when(handler.validateConnection(any())).thenReturn(SecretConnectionResult.active());

      SecretConnectionProbe probe = provider.prepareConnectionCheck(referenceAt(SECRET_LOCATION));
      // Everything the check needs was resolved during preparation; forget those interactions.
      clearInvocations(secretService, secretHandlerResolver);

      // Act
      probe.run();

      // Assert
      verifyNoInteractions(secretService);
      verifyNoInteractions(secretHandlerResolver);
    }
  }
}
