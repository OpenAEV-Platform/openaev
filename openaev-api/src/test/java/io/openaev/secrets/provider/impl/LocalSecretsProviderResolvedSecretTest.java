package io.openaev.secrets.provider.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretResolvedValue;
import io.openaev.secrets.provider.impl.handlers.SecretHandler;
import io.openaev.secrets.provider.impl.handlers.SecretHandlerResolver;
import io.openaev.secrets.service.SecretReferenceService;
import io.openaev.secrets.service.SecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocalSecretsProvider getResolvedSecret tests")
class LocalSecretsProviderResolvedSecretTest {

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

  @Test
  @DisplayName("Given a resolvable secret, should hand the handler resolved value back")
  void given_resolvableSecret_should_returnHandlerResolvedValue() {
    SecretReference reference = new SecretReference();
    reference.setLocation(SECRET_LOCATION);

    Secret secret = new HashSecret();
    SecretHandler handler = mock(SecretHandler.class);
    SecretResolvedValue resolvedValue = mock(SecretResolvedValue.class);

    when(secretService.findByIdOrThrow(SECRET_LOCATION)).thenReturn(secret);
    when(secretHandlerResolver.resolveFor(secret)).thenReturn(handler);
    when(handler.toResolvedValue(secret)).thenReturn(resolvedValue);

    SecretResolvedValue result = provider.getResolvedSecret(reference);

    assertThat(result).isSameAs(resolvedValue);
  }
}
