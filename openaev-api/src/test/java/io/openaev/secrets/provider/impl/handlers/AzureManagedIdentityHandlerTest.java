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
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_OTHER_ENVIRONMENT;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_SUBSCRIPTION_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_TENANT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureManagedIdentityRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureServicePrincipalRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureSystemAssignedManagedIdentityRequest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class AzureManagedIdentityHandlerTest extends IntegrationTest {

  private static final String MANDATORY_FIELDS_MESSAGE = "Azure environment is required";
  private static final String TYPE_MISMATCH_MESSAGE =
      "Secret type mismatch: expected AZURE_MANAGED_IDENTITY secret";

  @Autowired private AzureManagedIdentityHandler handler;

  @Nested
  @DisplayName("supports(Secret)")
  class SupportsSecret {

    @Test
    @DisplayName("Given an Azure managed identity secret, should be supported")
    void given_azureManagedIdentitySecret_should_beSupported() {
      // Arrange
      Secret secret = new AzureManagedIdentitySecret();

      // Act
      boolean supported = handler.supports(secret);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("Given another Azure secret type, should not be supported")
    void given_azureServicePrincipalSecret_should_notBeSupported() {
      // Arrange
      Secret secret = new AzureServicePrincipalSecret();

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
    @DisplayName("Given an Azure managed identity credential reference, should be supported")
    void given_azureManagedIdentityReference_should_beSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getAzureManagedIdentityReference());

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("Given the other Azure auth method, should not be supported")
    void given_azureServicePrincipalReference_should_notBeSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getAzureServicePrincipalReference());

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
    @DisplayName(
        "Given a system-assigned identity request, should build a secret without client id")
    void given_systemAssignedRequest_should_buildSecretWithoutClientId() {
      // Arrange
      SecretStoreRequest request = azureSystemAssignedManagedIdentityRequest();

      // Act
      Secret secret = handler.buildOrUpdate(null, request);

      // Assert
      assertThat(secret).isInstanceOf(AzureManagedIdentitySecret.class);
      AzureManagedIdentitySecret azureSecret = (AzureManagedIdentitySecret) secret;
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isNull();
      assertThat(azureSecret.getAzureSubscriptionId()).isNull();
    }

    @Test
    @DisplayName("Given a user-assigned identity request, should build a secret with client id")
    void given_userAssignedRequest_should_buildSecretWithClientId() {
      // Arrange
      SecretStoreRequest request = azureUserAssignedManagedIdentityRequest();

      // Act
      AzureManagedIdentitySecret azureSecret =
          (AzureManagedIdentitySecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(azureSecret.getAzureSubscriptionId()).isEqualTo(AZURE_SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("Given a request, should store the subscription id without encryption")
    void given_request_should_storeSubscriptionIdWithoutEncryption() {
      // Arrange
      SecretStoreRequest request = azureUserAssignedManagedIdentityRequest();

      // Act
      AzureManagedIdentitySecret azureSecret =
          (AzureManagedIdentitySecret) handler.buildOrUpdate(null, request);

      // Assert: that column is sized for clear text, encrypting it would overflow
      assertThat(azureSecret.getAzureSubscriptionId()).isEqualTo(AZURE_SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("Given a service principal request, should ignore its extra fields")
    void given_servicePrincipalRequest_should_ignoreExtraFields() {
      // Arrange: a managed identity secret has no client secret nor tenant id to store
      SecretStoreRequest request = azureServicePrincipalRequest();

      // Act
      AzureManagedIdentitySecret azureSecret =
          (AzureManagedIdentitySecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(azureSecret.toString()).doesNotContain(AZURE_CLIENT_SECRET, AZURE_TENANT_ID);
    }

    @Test
    @DisplayName("Given an existing secret of another type, should build a brand new secret")
    void given_existingSecretOfAnotherType_should_buildNewSecret() {
      // Arrange
      AzureServicePrincipalSecret existingSecret = new AzureServicePrincipalSecret();
      existingSecret.setAzureClientId("should-be-ignored");

      // Act
      Secret secret =
          handler.buildOrUpdate(existingSecret, azureSystemAssignedManagedIdentityRequest());

      // Assert
      assertThat(secret).isInstanceOf(AzureManagedIdentitySecret.class).isNotSameAs(existingSecret);
      assertThat(((AzureManagedIdentitySecret) secret).getAzureClientId()).isNull();
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - update")
  class BuildOrUpdateUpdate {

    private AzureManagedIdentitySecret existingSecret() {
      return (AzureManagedIdentitySecret)
          handler.buildOrUpdate(null, azureUserAssignedManagedIdentityRequest());
    }

    @Test
    @DisplayName("Given an existing secret, should update it in place")
    void given_existingSecret_should_updateItInPlace() {
      // Arrange
      AzureManagedIdentitySecret existingSecret = existingSecret();
      SecretStoreRequest request =
          azureManagedIdentityRequest(AZURE_OTHER_ENVIRONMENT, AZURE_OTHER_CLIENT_ID, null);

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(secret).isSameAs(existingSecret);
      assertThat(existingSecret.getAzureEnvironment()).isEqualTo(AZURE_OTHER_ENVIRONMENT);
      assertThat(existingSecret.getAzureClientId()).isEqualTo(AZURE_OTHER_CLIENT_ID);
    }

    @Test
    @DisplayName("Given a partial request, should keep the fields that were not provided")
    void given_partialRequest_should_keepNotProvidedFields() {
      // Arrange
      AzureManagedIdentitySecret existingSecret = existingSecret();
      SecretStoreRequest request = azureManagedIdentityRequest(AZURE_OTHER_ENVIRONMENT, null, null);

      // Act
      AzureManagedIdentitySecret azureSecret =
          (AzureManagedIdentitySecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_OTHER_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(azureSecret.getAzureSubscriptionId()).isEqualTo(AZURE_SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("Given an empty request on a complete secret, should leave it untouched")
    void given_emptyRequestOnCompleteSecret_should_leaveItUntouched() {
      // Arrange
      AzureManagedIdentitySecret existingSecret = existingSecret();

      // Act
      AzureManagedIdentitySecret azureSecret =
          (AzureManagedIdentitySecret) handler.buildOrUpdate(existingSecret, emptyRequest());

      // Assert
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(azureSecret.getAzureSubscriptionId()).isEqualTo(AZURE_SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("Given a system-assigned secret, should be upgradable to a user-assigned one")
    void given_systemAssignedSecret_should_beUpgradableToUserAssigned() {
      // Arrange
      AzureManagedIdentitySecret existingSecret =
          (AzureManagedIdentitySecret)
              handler.buildOrUpdate(null, azureSystemAssignedManagedIdentityRequest());
      SecretStoreRequest request =
          azureManagedIdentityRequest(null, AZURE_CLIENT_ID, AZURE_SUBSCRIPTION_ID);

      // Act
      AzureManagedIdentitySecret azureSecret =
          (AzureManagedIdentitySecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(azureSecret.getAzureSubscriptionId()).isEqualTo(AZURE_SUBSCRIPTION_ID);
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
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
          azureManagedIdentityRequest(null, AZURE_CLIENT_ID, AZURE_SUBSCRIPTION_ID);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("Given a request without client id, should not throw")
    void given_requestWithoutClientId_should_notThrow() {
      // Arrange
      SecretStoreRequest request =
          azureManagedIdentityRequest(AZURE_ENVIRONMENT, null, AZURE_SUBSCRIPTION_ID);

      // Act
      AzureManagedIdentitySecret azureSecret =
          (AzureManagedIdentitySecret) handler.buildOrUpdate(null, request);

      // Assert: the client id is optional, a system-assigned identity has none
      assertThat(azureSecret.getAzureClientId()).isNull();
    }

    @Test
    @DisplayName("Given a request without subscription id, should not throw")
    void given_requestWithoutSubscriptionId_should_notThrow() {
      // Arrange
      SecretStoreRequest request =
          azureManagedIdentityRequest(AZURE_ENVIRONMENT, AZURE_CLIENT_ID, null);

      // Act
      AzureManagedIdentitySecret azureSecret =
          (AzureManagedIdentitySecret) handler.buildOrUpdate(null, request);

      // Assert: the subscription id is optional
      assertThat(azureSecret.getAzureSubscriptionId()).isNull();
    }

    @Test
    @DisplayName("Given only an environment, should not throw")
    void given_onlyEnvironment_should_notThrow() {
      // Arrange
      SecretStoreRequest request = azureManagedIdentityRequest(AZURE_ENVIRONMENT, null, null);

      // Act
      AzureManagedIdentitySecret azureSecret =
          (AzureManagedIdentitySecret) handler.buildOrUpdate(null, request);

      // Assert: the environment is the only mandatory field
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
    }

    @Test
    @DisplayName("Given an unsupported environment, should throw")
    void given_unsupportedEnvironment_should_throw() {
      // Arrange
      SecretStoreRequest request =
          azureManagedIdentityRequest("NotAnAzureCloud", AZURE_CLIENT_ID, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported Azure environment: NotAnAzureCloud");
    }

    @Test
    @DisplayName("Given an unsupported environment, should not overwrite the stored one")
    void given_unsupportedEnvironment_should_notOverwriteStoredOne() {
      // Arrange
      AzureManagedIdentitySecret existingSecret =
          (AzureManagedIdentitySecret)
              handler.buildOrUpdate(null, azureSystemAssignedManagedIdentityRequest());
      SecretStoreRequest request = azureManagedIdentityRequest("NotAnAzureCloud", null, null);

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
  }

  @Nested
  @DisplayName("toMetadata")
  class ToMetadata {

    @Test
    @DisplayName(
        "Given a user-assigned identity secret, should expose environment, client id and subscription id")
    void given_userAssignedSecret_should_exposeNonSensitiveIdentifiers() {
      // Arrange
      AzureManagedIdentitySecret secret =
          (AzureManagedIdentitySecret)
              handler.buildOrUpdate(null, azureUserAssignedManagedIdentityRequest());

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.azureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(metadata.azureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(metadata.azureSubscriptionId()).isEqualTo(AZURE_SUBSCRIPTION_ID);
      assertThat(metadata.azureTenantId()).isNull();
      assertThat(metadata.username()).isNull();
      assertThat(metadata.hashAlgorithm()).isNull();
      assertThat(metadata.awsDefaultRegion()).isNull();
      assertThat(metadata.awsAccessKeyId()).isNull();
      assertThat(metadata.awsRoleArn()).isNull();
      assertThat(metadata.awsSourceIdentityType()).isNull();
      assertThat(metadata.awsSourceProfileAccessKeyId()).isNull();
    }

    @Test
    @DisplayName("Given a system-assigned identity secret, should expose a null client id")
    void given_systemAssignedSecret_should_exposeNullClientId() {
      // Arrange
      AzureManagedIdentitySecret secret =
          (AzureManagedIdentitySecret)
              handler.buildOrUpdate(null, azureSystemAssignedManagedIdentityRequest());

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert: the environment still tells the form that a secret is stored
      assertThat(metadata.azureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(metadata.azureClientId()).isNull();
    }

    @Test
    @DisplayName("Given a secret of another type, should throw")
    void given_secretOfAnotherType_should_throw() {
      // Arrange
      Secret secret = new AzureServicePrincipalSecret();

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
