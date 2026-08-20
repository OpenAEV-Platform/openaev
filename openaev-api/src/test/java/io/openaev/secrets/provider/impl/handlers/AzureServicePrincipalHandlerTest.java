package io.openaev.secrets.provider.impl.handlers;

import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAwsAccessKeyReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAzureManagedIdentityReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAzureServicePrincipalReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getHashReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getNonCredentialReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getUsernamePasswordReference;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_ENVIRONMENT;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_OTHER_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_OTHER_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_OTHER_ENVIRONMENT;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_OTHER_TENANT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_SUBSCRIPTION_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_TENANT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureServicePrincipalRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureServicePrincipalRequestWithSubscription;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureUserAssignedManagedIdentityRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.emptyRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AwsAccessKeySecret;
import io.openaev.database.model.AzureManagedIdentitySecret;
import io.openaev.database.model.AzureServicePrincipalSecret;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Secret;
import io.openaev.database.model.UsernamePasswordSecret;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class AzureServicePrincipalHandlerTest extends IntegrationTest {

  private static final String MANDATORY_FIELDS_MESSAGE =
      "Azure environment, client id, client secret and tenant id are required";
  private static final String TYPE_MISMATCH_MESSAGE =
      "Secret type mismatch: expected AZURE_SERVICE_PRINCIPAL secret";

  @Autowired private AzureServicePrincipalHandler handler;
  @Autowired private NativeEncryptionService nativeEncryptionService;

  @Nested
  @DisplayName("supports(Secret)")
  class SupportsSecret {

    @Test
    @DisplayName("Given an Azure service principal secret, should be supported")
    void given_azureServicePrincipalSecret_should_beSupported() {
      // Arrange
      Secret secret = new AzureServicePrincipalSecret();

      // Act
      boolean supported = handler.supports(secret);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("Given another Azure secret type, should not be supported")
    void given_azureManagedIdentitySecret_should_notBeSupported() {
      // Arrange
      Secret secret = new AzureManagedIdentitySecret();

      // Act
      boolean supported = handler.supports(secret);

      // Assert
      assertThat(supported).isFalse();
    }

    @Test
    @DisplayName("Given a non Azure secret type, should not be supported")
    void given_nonAzureSecret_should_notBeSupported() {
      // Arrange & Act & Assert
      assertThat(handler.supports(new AwsAccessKeySecret())).isFalse();
      assertThat(handler.supports(new UsernamePasswordSecret())).isFalse();
      assertThat(handler.supports(new HashSecret())).isFalse();
      assertThat(handler.supports(new Secret())).isFalse();
    }

    @Test
    @DisplayName("Given a null secret, should not be supported")
    void given_nullSecret_should_notBeSupported() {
      // Arrange & Act
      boolean supported = handler.supports((Secret) null);

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("supports(SecretReference)")
  class SupportsSecretReference {

    @Test
    @DisplayName("Given an Azure service principal credential reference, should be supported")
    void given_azureServicePrincipalReference_should_beSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getAzureServicePrincipalReference());

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("Given the other Azure auth method, should not be supported")
    void given_azureManagedIdentityReference_should_notBeSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getAzureManagedIdentityReference());

      // Assert
      assertThat(supported).isFalse();
    }

    @Test
    @DisplayName("Given another credential auth method, should not be supported")
    void given_otherAuthMethodReference_should_notBeSupported() {
      // Arrange & Act & Assert
      assertThat(handler.supports(getAwsAccessKeyReference())).isFalse();
      assertThat(handler.supports(getUsernamePasswordReference())).isFalse();
      assertThat(handler.supports(getHashReference())).isFalse();
    }

    @Test
    @DisplayName("Given a non credential reference, should not be supported")
    void given_nonCredentialReference_should_notBeSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getNonCredentialReference());

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - create")
  class BuildOrUpdateCreate {

    @Test
    @DisplayName("Given a complete request, should build an Azure service principal secret")
    void given_completeRequest_should_buildAzureServicePrincipalSecret() {
      // Arrange
      SecretStoreRequest request = azureServicePrincipalRequest();

      // Act
      Secret secret = handler.buildOrUpdate(null, request);

      // Assert
      assertThat(secret).isInstanceOf(AzureServicePrincipalSecret.class);
      AzureServicePrincipalSecret azureSecret = (AzureServicePrincipalSecret) secret;
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(azureSecret.getAzureTenantId()).isEqualTo(AZURE_TENANT_ID);
      assertThat(nativeEncryptionService.decrypt(azureSecret.getAzureClientSecret()))
          .isEqualTo(AZURE_CLIENT_SECRET);
      assertThat(azureSecret.getAzureSubscriptionId()).isNull();
    }

    @Test
    @DisplayName("Given a request with a subscription, should store the subscription id")
    void given_requestWithSubscription_should_storeSubscriptionId() {
      // Arrange
      SecretStoreRequest request = azureServicePrincipalRequestWithSubscription();

      // Act
      AzureServicePrincipalSecret azureSecret =
          (AzureServicePrincipalSecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(azureSecret.getAzureSubscriptionId()).isEqualTo(AZURE_SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("Given a request, should never store the client secret in clear text")
    void given_request_should_notStoreClientSecretInClearText() {
      // Arrange
      SecretStoreRequest request = azureServicePrincipalRequest();

      // Act
      AzureServicePrincipalSecret azureSecret =
          (AzureServicePrincipalSecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(azureSecret.getAzureClientSecret()).isNotEqualTo(AZURE_CLIENT_SECRET);
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
    }

    @Test
    @DisplayName("Given a request, should store tenant and subscription ids without encryption")
    void given_request_should_storeTenantAndSubscriptionIdsWithoutEncryption() {
      // Arrange
      SecretStoreRequest request = azureServicePrincipalRequestWithSubscription();

      // Act
      AzureServicePrincipalSecret azureSecret =
          (AzureServicePrincipalSecret) handler.buildOrUpdate(null, request);

      // Assert: those columns are sized for clear text, encrypting them would overflow
      assertThat(azureSecret.getAzureTenantId()).isEqualTo(AZURE_TENANT_ID);
      assertThat(azureSecret.getAzureSubscriptionId()).isEqualTo(AZURE_SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("Given an existing secret of another type, should build a brand new secret")
    void given_existingSecretOfAnotherType_should_buildNewSecret() {
      // Arrange
      AzureManagedIdentitySecret existingSecret = new AzureManagedIdentitySecret();
      existingSecret.setAzureClientId("should-be-ignored");

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, azureServicePrincipalRequest());

      // Assert
      assertThat(secret)
          .isInstanceOf(AzureServicePrincipalSecret.class)
          .isNotSameAs(existingSecret);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - update")
  class BuildOrUpdateUpdate {

    private AzureServicePrincipalSecret existingSecret() {
      return (AzureServicePrincipalSecret)
          handler.buildOrUpdate(null, azureServicePrincipalRequestWithSubscription());
    }

    @Test
    @DisplayName("Given an existing secret, should update it in place")
    void given_existingSecret_should_updateItInPlace() {
      // Arrange
      AzureServicePrincipalSecret existingSecret = existingSecret();
      SecretStoreRequest request =
          azureServicePrincipalRequest(
              AZURE_OTHER_ENVIRONMENT,
              AZURE_OTHER_CLIENT_ID,
              AZURE_OTHER_CLIENT_SECRET,
              AZURE_OTHER_TENANT_ID,
              null);

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(secret).isSameAs(existingSecret);
      assertThat(existingSecret.getAzureEnvironment()).isEqualTo(AZURE_OTHER_ENVIRONMENT);
      assertThat(existingSecret.getAzureClientId()).isEqualTo(AZURE_OTHER_CLIENT_ID);
      assertThat(existingSecret.getAzureTenantId()).isEqualTo(AZURE_OTHER_TENANT_ID);
      assertThat(nativeEncryptionService.decrypt(existingSecret.getAzureClientSecret()))
          .isEqualTo(AZURE_OTHER_CLIENT_SECRET);
    }

    @Test
    @DisplayName("Given a partial request, should keep the fields that were not provided")
    void given_partialRequest_should_keepNotProvidedFields() {
      // Arrange
      AzureServicePrincipalSecret existingSecret = existingSecret();
      SecretStoreRequest request =
          azureServicePrincipalRequest(AZURE_OTHER_ENVIRONMENT, null, null, null, null);

      // Act
      AzureServicePrincipalSecret azureSecret =
          (AzureServicePrincipalSecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_OTHER_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(azureSecret.getAzureTenantId()).isEqualTo(AZURE_TENANT_ID);
      assertThat(azureSecret.getAzureSubscriptionId()).isEqualTo(AZURE_SUBSCRIPTION_ID);
      assertThat(nativeEncryptionService.decrypt(azureSecret.getAzureClientSecret()))
          .isEqualTo(AZURE_CLIENT_SECRET);
    }

    @Test
    @DisplayName("Given an empty request on a complete secret, should leave it untouched")
    void given_emptyRequestOnCompleteSecret_should_leaveItUntouched() {
      // Arrange
      AzureServicePrincipalSecret existingSecret = existingSecret();
      String encryptedClientSecret = existingSecret.getAzureClientSecret();

      // Act
      AzureServicePrincipalSecret azureSecret =
          (AzureServicePrincipalSecret) handler.buildOrUpdate(existingSecret, emptyRequest());

      // Assert
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(azureSecret.getAzureTenantId()).isEqualTo(AZURE_TENANT_ID);
      assertThat(azureSecret.getAzureClientSecret()).isEqualTo(encryptedClientSecret);
    }

    @Test
    @DisplayName("Given a new client secret, should re-encrypt it with a new cipher text")
    void given_newClientSecret_should_reEncryptIt() {
      // Arrange
      AzureServicePrincipalSecret existingSecret = existingSecret();
      String previousCipherText = existingSecret.getAzureClientSecret();
      SecretStoreRequest request =
          azureServicePrincipalRequest(null, null, AZURE_OTHER_CLIENT_SECRET, null, null);

      // Act
      AzureServicePrincipalSecret azureSecret =
          (AzureServicePrincipalSecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(azureSecret.getAzureClientSecret()).isNotEqualTo(previousCipherText);
      assertThat(nativeEncryptionService.decrypt(azureSecret.getAzureClientSecret()))
          .isEqualTo(AZURE_OTHER_CLIENT_SECRET);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - validation")
  class BuildOrUpdateValidation {

    @Test
    @DisplayName("Given a request without environment, should throw")
    void given_requestWithoutEnvironment_should_throw() {
      // Arrange
      SecretStoreRequest request =
          azureServicePrincipalRequest(
              null, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("Given a request without client id, should throw")
    void given_requestWithoutClientId_should_throw() {
      // Arrange
      SecretStoreRequest request =
          azureServicePrincipalRequest(
              AZURE_ENVIRONMENT, null, AZURE_CLIENT_SECRET, AZURE_TENANT_ID, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("Given a request without client secret, should throw")
    void given_requestWithoutClientSecret_should_throw() {
      // Arrange
      SecretStoreRequest request =
          azureServicePrincipalRequest(
              AZURE_ENVIRONMENT, AZURE_CLIENT_ID, null, AZURE_TENANT_ID, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("Given a request without tenant id, should throw")
    void given_requestWithoutTenantId_should_throw() {
      // Arrange
      SecretStoreRequest request =
          azureServicePrincipalRequest(
              AZURE_ENVIRONMENT, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, null, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("Given a request without subscription id, should not throw")
    void given_requestWithoutSubscriptionId_should_notThrow() {
      // Arrange
      SecretStoreRequest request = azureServicePrincipalRequest();

      // Act
      AzureServicePrincipalSecret azureSecret =
          (AzureServicePrincipalSecret) handler.buildOrUpdate(null, request);

      // Assert: the subscription id is optional
      assertThat(azureSecret.getAzureSubscriptionId()).isNull();
    }

    @Test
    @DisplayName("Given an unsupported environment, should throw")
    void given_unsupportedEnvironment_should_throw() {
      // Arrange
      SecretStoreRequest request =
          azureServicePrincipalRequest(
              "NotAnAzureCloud", AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported Azure environment: NotAnAzureCloud");
    }

    @Test
    @DisplayName("Given an unsupported environment, should not overwrite the stored one")
    void given_unsupportedEnvironment_should_notOverwriteStoredOne() {
      // Arrange
      AzureServicePrincipalSecret existingSecret =
          (AzureServicePrincipalSecret) handler.buildOrUpdate(null, azureServicePrincipalRequest());
      SecretStoreRequest request =
          azureServicePrincipalRequest("NotAnAzureCloud", null, null, null, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(existingSecret, request))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(existingSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
    }

    @Test
    @DisplayName("Given an empty request and no existing secret, should throw")
    void given_emptyRequestAndNoExistingSecret_should_throw() {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, emptyRequest()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("Given a request for another auth method, should throw")
    void given_managedIdentityRequest_should_throw() {
      // Arrange
      SecretStoreRequest request = azureUserAssignedManagedIdentityRequest();

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }
  }

  @Nested
  @DisplayName("toMetadata")
  class ToMetadata {

    @Test
    @DisplayName("Given an Azure service principal secret, should expose environment and client id")
    void given_azureServicePrincipalSecret_should_exposeEnvironmentAndClientIdOnly() {
      // Arrange
      AzureServicePrincipalSecret secret =
          (AzureServicePrincipalSecret)
              handler.buildOrUpdate(null, azureServicePrincipalRequestWithSubscription());

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.azureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(metadata.azureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(metadata.username()).isNull();
      assertThat(metadata.hashAlgorithm()).isNull();
      assertThat(metadata.awsDefaultRegion()).isNull();
      assertThat(metadata.awsAccessKeyId()).isNull();
      assertThat(metadata.awsRoleArn()).isNull();
      assertThat(metadata.awsSourceIdentityType()).isNull();
      assertThat(metadata.awsSourceProfileAccessKeyId()).isNull();
    }

    @Test
    @DisplayName("Given an Azure service principal secret, should never expose sensitive values")
    void given_azureServicePrincipalSecret_should_notExposeSensitiveValues() {
      // Arrange
      AzureServicePrincipalSecret secret =
          (AzureServicePrincipalSecret)
              handler.buildOrUpdate(null, azureServicePrincipalRequestWithSubscription());

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.toString())
          .doesNotContain(AZURE_CLIENT_SECRET)
          .doesNotContain(secret.getAzureClientSecret())
          .doesNotContain(AZURE_TENANT_ID)
          .doesNotContain(AZURE_SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("Given a secret of another type, should throw")
    void given_secretOfAnotherType_should_throw() {
      // Arrange
      Secret secret = new AzureManagedIdentitySecret();

      // Act & Assert
      assertThatThrownBy(() -> handler.toMetadata(secret))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(TYPE_MISMATCH_MESSAGE);
    }

    @Test
    @DisplayName("Given a null secret, should throw")
    void given_nullSecret_should_throw() {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> handler.toMetadata(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(TYPE_MISMATCH_MESSAGE);
    }
  }
}
