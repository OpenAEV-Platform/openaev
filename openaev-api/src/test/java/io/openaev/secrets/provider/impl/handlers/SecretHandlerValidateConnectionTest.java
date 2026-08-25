package io.openaev.secrets.provider.impl.handlers;

import static io.openaev.secrets.provider.SecretConnectionResult.OUTCOME.ACTIVE;
import static io.openaev.secrets.provider.SecretConnectionResult.OUTCOME.UNSUPPORTED;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.AzureManagedIdentitySecret;
import io.openaev.database.model.AzureServicePrincipalSecret;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.UsernamePasswordSecret;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.impl.validators.AzureCredentialValidator;
import io.openaev.service.connector_instances.NativeEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@code validateConnection} side of the secret handlers.
 *
 * <p>Kept separate from the existing {@code Azure*HandlerTest} integration classes on purpose: the
 * point here is to prove the handler DELEGATES correctly (and decrypts before delegating), which
 * needs a mocked validator, not a Spring context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecretHandler validateConnection tests")
class SecretHandlerValidateConnectionTest {

  private static final String ENCRYPTED_CLIENT_SECRET = "encrypted-azureClientSecret";

  @Nested
  @DisplayName("Azure service principal handler")
  class AzureServicePrincipal {

    @Mock private AzureCredentialValidator azureCredentialValidator;
    @Mock private NativeEncryptionService nativeEncryptionService;

    private AzureServicePrincipalHandler handler;

    @BeforeEach
    void setUp() {
      handler = new AzureServicePrincipalHandler(nativeEncryptionService, azureCredentialValidator);
    }

    @Test
    @DisplayName("The stored client secret is decrypted before it reaches the validator")
    void given_servicePrincipalSecret_should_decryptThenDelegate() {
      // Arrange
      AzureServicePrincipalSecret secret = new AzureServicePrincipalSecret();
      secret.setAzureEnvironment(AZURE_ENVIRONMENT);
      secret.setAzureTenantId(AZURE_TENANT_ID);
      secret.setAzureClientId(AZURE_CLIENT_ID);
      secret.setAzureClientSecret(ENCRYPTED_CLIENT_SECRET);
      secret.setAzureSubscriptionId(AZURE_SUBSCRIPTION_ID);
      when(nativeEncryptionService.decrypt(ENCRYPTED_CLIENT_SECRET))
          .thenReturn(AZURE_CLIENT_SECRET);
      when(azureCredentialValidator.validateServicePrincipal(any(), any(), any(), any(), any()))
          .thenReturn(SecretConnectionResult.active());

      // Act
      SecretConnectionResult result = handler.validateConnection(secret);

      // Assert
      assertThat(result.outcome()).isEqualTo(ACTIVE);
      verify(nativeEncryptionService).decrypt(ENCRYPTED_CLIENT_SECRET);
      verify(azureCredentialValidator)
          .validateServicePrincipal(
              eq(AZURE_ENVIRONMENT),
              eq(AZURE_TENANT_ID),
              eq(AZURE_CLIENT_ID),
              // The plaintext, never the stored ciphertext.
              eq(AZURE_CLIENT_SECRET),
              eq(AZURE_SUBSCRIPTION_ID));
    }

    @Test
    @DisplayName("A secret of another type is rejected instead of being probed")
    void given_wrongSecretType_should_throw() {
      // Arrange
      HashSecret wrongType = new HashSecret();

      // Act & Assert
      assertThatThrownBy(() -> handler.validateConnection(wrongType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("AZURE_SERVICE_PRINCIPAL");
      verifyNoInteractions(azureCredentialValidator);
    }
  }

  @Nested
  @DisplayName("Azure managed identity handler")
  class AzureManagedIdentity {

    @Mock private AzureCredentialValidator azureCredentialValidator;

    private AzureManagedIdentityHandler handler;

    @BeforeEach
    void setUp() {
      handler = new AzureManagedIdentityHandler(azureCredentialValidator);
    }

    @Test
    @DisplayName("A user-assigned identity is delegated with its client id")
    void given_userAssignedIdentity_should_delegateWithClientId() {
      // Arrange
      AzureManagedIdentitySecret secret = new AzureManagedIdentitySecret();
      secret.setAzureEnvironment(AZURE_ENVIRONMENT);
      secret.setAzureClientId(AZURE_CLIENT_ID);
      secret.setAzureSubscriptionId(AZURE_SUBSCRIPTION_ID);
      when(azureCredentialValidator.validateManagedIdentity(any(), any(), any()))
          .thenReturn(SecretConnectionResult.active());

      // Act
      SecretConnectionResult result = handler.validateConnection(secret);

      // Assert
      assertThat(result.outcome()).isEqualTo(ACTIVE);
      verify(azureCredentialValidator)
          .validateManagedIdentity(
              eq(AZURE_ENVIRONMENT), eq(AZURE_CLIENT_ID), eq(AZURE_SUBSCRIPTION_ID));
    }

    @Test
    @DisplayName("A system-assigned identity is delegated without a client id")
    void given_systemAssignedIdentity_should_delegateWithoutClientId() {
      // Arrange — no client id and no subscription is the system-assigned shape.
      AzureManagedIdentitySecret secret = new AzureManagedIdentitySecret();
      secret.setAzureEnvironment(AZURE_ENVIRONMENT);
      when(azureCredentialValidator.validateManagedIdentity(any(), any(), any()))
          .thenReturn(SecretConnectionResult.active());

      // Act
      handler.validateConnection(secret);

      // Assert
      verify(azureCredentialValidator)
          .validateManagedIdentity(eq(AZURE_ENVIRONMENT), isNull(), isNull());
    }

    @Test
    @DisplayName("A secret of another type is rejected instead of being probed")
    void given_wrongSecretType_should_throw() {
      // Arrange
      AzureServicePrincipalSecret wrongType = new AzureServicePrincipalSecret();

      // Act & Assert
      assertThatThrownBy(() -> handler.validateConnection(wrongType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("AZURE_MANAGED_IDENTITY");
      verifyNoInteractions(azureCredentialValidator);
    }
  }

  @Nested
  @DisplayName("Handlers with no remote counterpart")
  class NonValidatingHandlers {

    @Test
    @DisplayName("A hash handler reports the check as unsupported through the interface default")
    void given_hashHandler_should_returnUnsupported() {
      // Arrange — the default must keep working with zero code in the handler. The encryption
      // service is irrelevant here: validateConnection never touches it.
      SecretHandler handler = new HashHandler(null);

      // Act
      SecretConnectionResult result = handler.validateConnection(new HashSecret());

      // Assert
      assertThat(result.outcome()).isEqualTo(UNSUPPORTED);
      // Unsupported is not "verified": the reference must not be stamped.
      assertThat(result.wasChecked()).isFalse();
      assertThat(result.statusToPersist()).isEmpty();
    }

    @Test
    @DisplayName("A username/password handler reports the check as unsupported")
    void given_usernamePasswordHandler_should_returnUnsupported() {
      // Arrange
      SecretHandler handler = new UsernamePasswordHandler(null);

      // Act
      SecretConnectionResult result = handler.validateConnection(new UsernamePasswordSecret());

      // Assert
      assertThat(result.outcome()).isEqualTo(UNSUPPORTED);
      assertThat(result.wasChecked()).isFalse();
    }
  }
}
