package io.openaev.secrets.provider.impl.handlers;

import static io.openaev.database.model.SecretReference.SECRET_STATUS.ACTIVE;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.UNSUPPORTED;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.database.model.AwsAccessKeySecret;
import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AzureManagedIdentitySecret;
import io.openaev.database.model.AzureServicePrincipalSecret;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.UsernamePasswordSecret;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.impl.validators.AwsCredentialConnectivityCheck;
import io.openaev.secrets.provider.impl.validators.AzureCredentialConnectivityCheck;
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

  private static final String ENCRYPTED_AWS_SECRET_ACCESS_KEY = "encrypted-awsSecretAccessKey";
  private static final String ENCRYPTED_AWS_SESSION_TOKEN = "encrypted-awsSessionToken";
  private static final String ENCRYPTED_AWS_EXTERNAL_ID = "encrypted-awsExternalId";
  private static final String ENCRYPTED_AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY =
      "encrypted-awsSourceProfileSecretAccessKey";
  private static final String ENCRYPTED_CLIENT_SECRET = "encrypted-azureClientSecret";

  @Nested
  @DisplayName("AWS access key handler")
  class AwsAccessKey {

    @Mock private AwsCredentialConnectivityCheck awsCredentialConnectivityCheck;
    @Mock private NativeEncryptionService nativeEncryptionService;

    private AwsAccessKeyHandler handler;

    @BeforeEach
    void setUp() {
      handler = new AwsAccessKeyHandler(nativeEncryptionService, awsCredentialConnectivityCheck);
    }

    @Nested
    @DisplayName("AWS assume role handler")
    class AwsAssumeRole {

      @Mock private AwsCredentialConnectivityCheck awsCredentialConnectivityCheck;

      @Mock private NativeEncryptionService nativeEncryptionService;

      private AwsAssumeRoleHandler handler;

      @BeforeEach
      void setUp() {
        handler = new AwsAssumeRoleHandler(nativeEncryptionService, awsCredentialConnectivityCheck);
      }

      @Test
      @DisplayName(
          "The stored external id and source profile secret are decrypted before they reach the validator")
      void given_staticSourceIdentity_should_decryptThenDelegate() {
        // Arrange
        AwsAssumeRoleSecret secret = new AwsAssumeRoleSecret();
        secret.setAwsDefaultRegion(AWS_DEFAULT_REGION);
        secret.setAwsRoleArn(AWS_ROLE_ARN);
        secret.setAwsExternalId(ENCRYPTED_AWS_EXTERNAL_ID);
        secret.setAwsSourceIdentityType(
            AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY);
        secret.setAwsSourceProfileAccessKeyId(AWS_SOURCE_PROFILE_ACCESS_KEY_ID);
        secret.setAwsSourceProfileSecretAccessKey(ENCRYPTED_AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
        when(nativeEncryptionService.decrypt(ENCRYPTED_AWS_EXTERNAL_ID))
            .thenReturn(AWS_EXTERNAL_ID);
        when(nativeEncryptionService.decrypt(ENCRYPTED_AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY))
            .thenReturn(AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
        when(awsCredentialConnectivityCheck.validateAssumeRole(
                any(), any(), any(), any(), any(), any()))
            .thenReturn(SecretConnectionResult.active());

        // Act
        SecretConnectionResult result = handler.validateConnection(secret);

        // Assert
        assertThat(result.status()).isEqualTo(ACTIVE);
        verify(nativeEncryptionService).decrypt(ENCRYPTED_AWS_EXTERNAL_ID);
        verify(nativeEncryptionService).decrypt(ENCRYPTED_AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
        verify(awsCredentialConnectivityCheck)
            .validateAssumeRole(
                eq(AWS_DEFAULT_REGION),
                eq(AWS_ROLE_ARN),
                eq(AWS_EXTERNAL_ID),
                eq(AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY),
                eq(AWS_SOURCE_PROFILE_ACCESS_KEY_ID),
                eq(AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY));
      }

      @Test
      @DisplayName("An instance default identity delegates without source profile secret")
      void given_instanceDefaultSourceIdentity_should_delegateWithNullSourceSecret() {
        // Arrange
        AwsAssumeRoleSecret secret = new AwsAssumeRoleSecret();
        secret.setAwsDefaultRegion(AWS_DEFAULT_REGION);
        secret.setAwsRoleArn(AWS_ROLE_ARN);
        secret.setAwsSourceIdentityType(
            AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.INSTANCE_DEFAULT);
        when(awsCredentialConnectivityCheck.validateAssumeRole(
                any(), any(), isNull(), any(), isNull(), isNull()))
            .thenReturn(SecretConnectionResult.active());

        // Act
        handler.validateConnection(secret);

        // Assert
        verify(nativeEncryptionService, never())
            .decrypt(ENCRYPTED_AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
        verify(awsCredentialConnectivityCheck)
            .validateAssumeRole(
                eq(AWS_DEFAULT_REGION),
                eq(AWS_ROLE_ARN),
                isNull(),
                eq(AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.INSTANCE_DEFAULT),
                isNull(),
                isNull());
      }

      @Test
      @DisplayName("A secret of another type is rejected instead of being probed")
      void given_wrongSecretType_should_throw() {
        // Arrange
        HashSecret wrongType = new HashSecret();

        // Act & Assert
        assertThatThrownBy(() -> handler.validateConnection(wrongType))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("AWS_ASSUME_ROLE");
        verifyNoInteractions(awsCredentialConnectivityCheck);
      }
    }

    @Test
    @DisplayName("The stored AWS secrets are decrypted before they reach the validator")
    void given_accessKeySecretWithSessionToken_should_decryptThenDelegate() {
      // Arrange
      AwsAccessKeySecret secret = new AwsAccessKeySecret();
      secret.setAwsDefaultRegion(AWS_DEFAULT_REGION);
      secret.setAwsAccessKeyId(AWS_ACCESS_KEY_ID);
      secret.setAwsSecretAccessKey(ENCRYPTED_AWS_SECRET_ACCESS_KEY);
      secret.setAwsSessionToken(ENCRYPTED_AWS_SESSION_TOKEN);
      when(nativeEncryptionService.decrypt(ENCRYPTED_AWS_SECRET_ACCESS_KEY))
          .thenReturn(AWS_SECRET_ACCESS_KEY);
      when(nativeEncryptionService.decrypt(ENCRYPTED_AWS_SESSION_TOKEN))
          .thenReturn(AWS_SESSION_TOKEN);
      when(awsCredentialConnectivityCheck.validateAccessKey(any(), any(), any(), any()))
          .thenReturn(SecretConnectionResult.active());

      // Act
      SecretConnectionResult result = handler.validateConnection(secret);

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
      verify(nativeEncryptionService).decrypt(ENCRYPTED_AWS_SECRET_ACCESS_KEY);
      verify(nativeEncryptionService).decrypt(ENCRYPTED_AWS_SESSION_TOKEN);
      verify(awsCredentialConnectivityCheck)
          .validateAccessKey(
              eq(AWS_DEFAULT_REGION),
              eq(AWS_ACCESS_KEY_ID),
              eq(AWS_SECRET_ACCESS_KEY),
              eq(AWS_SESSION_TOKEN));
    }

    @Test
    @DisplayName("A blank session token is not decrypted and delegates as null")
    void given_blankSessionToken_should_delegateWithNullToken() {
      // Arrange
      AwsAccessKeySecret secret = new AwsAccessKeySecret();
      secret.setAwsDefaultRegion(AWS_DEFAULT_REGION);
      secret.setAwsAccessKeyId(AWS_ACCESS_KEY_ID);
      secret.setAwsSecretAccessKey(ENCRYPTED_AWS_SECRET_ACCESS_KEY);
      secret.setAwsSessionToken(" ");
      when(nativeEncryptionService.decrypt(ENCRYPTED_AWS_SECRET_ACCESS_KEY))
          .thenReturn(AWS_SECRET_ACCESS_KEY);
      when(awsCredentialConnectivityCheck.validateAccessKey(any(), any(), any(), isNull()))
          .thenReturn(SecretConnectionResult.active());

      // Act
      handler.validateConnection(secret);

      // Assert
      verify(nativeEncryptionService).decrypt(ENCRYPTED_AWS_SECRET_ACCESS_KEY);
      verify(nativeEncryptionService, never()).decrypt(" ");
      verify(awsCredentialConnectivityCheck)
          .validateAccessKey(
              eq(AWS_DEFAULT_REGION), eq(AWS_ACCESS_KEY_ID), eq(AWS_SECRET_ACCESS_KEY), isNull());
    }

    @Test
    @DisplayName("A secret of another type is rejected instead of being probed")
    void given_wrongSecretType_should_throw() {
      // Arrange
      HashSecret wrongType = new HashSecret();

      // Act & Assert
      assertThatThrownBy(() -> handler.validateConnection(wrongType))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("AWS_ACCESS_KEY");
      verifyNoInteractions(awsCredentialConnectivityCheck);
    }
  }

  @Nested
  @DisplayName("Azure service principal handler")
  class AzureServicePrincipal {

    @Mock private AzureCredentialConnectivityCheck azureCredentialConnectivityCheck;
    @Mock private NativeEncryptionService nativeEncryptionService;

    private AzureServicePrincipalHandler handler;

    @BeforeEach
    void setUp() {
      handler =
          new AzureServicePrincipalHandler(
              nativeEncryptionService, azureCredentialConnectivityCheck);
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
      when(azureCredentialConnectivityCheck.validateServicePrincipal(
              any(), any(), any(), any(), any()))
          .thenReturn(SecretConnectionResult.active());

      // Act
      SecretConnectionResult result = handler.validateConnection(secret);

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
      verify(nativeEncryptionService).decrypt(ENCRYPTED_CLIENT_SECRET);
      verify(azureCredentialConnectivityCheck)
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
      verifyNoInteractions(azureCredentialConnectivityCheck);
    }
  }

  @Nested
  @DisplayName("Azure managed identity handler")
  class AzureManagedIdentity {

    @Mock private AzureCredentialConnectivityCheck azureCredentialConnectivityCheck;

    private AzureManagedIdentityHandler handler;

    @BeforeEach
    void setUp() {
      handler = new AzureManagedIdentityHandler(azureCredentialConnectivityCheck);
    }

    @Test
    @DisplayName("A user-assigned identity is delegated with its client id")
    void given_userAssignedIdentity_should_delegateWithClientId() {
      // Arrange
      AzureManagedIdentitySecret secret = new AzureManagedIdentitySecret();
      secret.setAzureEnvironment(AZURE_ENVIRONMENT);
      secret.setAzureClientId(AZURE_CLIENT_ID);
      secret.setAzureSubscriptionId(AZURE_SUBSCRIPTION_ID);
      when(azureCredentialConnectivityCheck.validateManagedIdentity(any(), any(), any()))
          .thenReturn(SecretConnectionResult.active());

      // Act
      SecretConnectionResult result = handler.validateConnection(secret);

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
      verify(azureCredentialConnectivityCheck)
          .validateManagedIdentity(
              eq(AZURE_ENVIRONMENT), eq(AZURE_CLIENT_ID), eq(AZURE_SUBSCRIPTION_ID));
    }

    @Test
    @DisplayName("A system-assigned identity is delegated without a client id")
    void given_systemAssignedIdentity_should_delegateWithoutClientId() {
      // Arrange — no client id and no subscription is the system-assigned shape.
      AzureManagedIdentitySecret secret = new AzureManagedIdentitySecret();
      secret.setAzureEnvironment(AZURE_ENVIRONMENT);
      when(azureCredentialConnectivityCheck.validateManagedIdentity(any(), any(), any()))
          .thenReturn(SecretConnectionResult.active());

      // Act
      handler.validateConnection(secret);

      // Assert
      verify(azureCredentialConnectivityCheck)
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
      verifyNoInteractions(azureCredentialConnectivityCheck);
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
      assertThat(result.status()).isEqualTo(UNSUPPORTED);
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
      assertThat(result.status()).isEqualTo(UNSUPPORTED);
      assertThat(result.wasChecked()).isFalse();
      assertThat(result.statusToPersist()).isEmpty();
    }
  }
}
