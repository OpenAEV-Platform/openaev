package io.openaev.secrets.provider.impl.handlers;

import static io.openaev.secrets.provider.SecretConnectionDetails.INVALID_CONFIGURATION;
import static io.openaev.secrets.provider.impl.handlers.GcpServiceAccountHandler.MANDATORY_FIELDS_MESSAGE;
import static io.openaev.secrets.provider.impl.handlers.GcpServiceAccountHandler.TYPE_MISMATCH_MESSAGE;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAwsAccessKeyReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAzureServicePrincipalReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getGcpOAuth2Reference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getGcpServiceAccountReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getHashReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getNonCredentialReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getUsernamePasswordReference;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_PRIVATE_KEY_JSON;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_PROJECT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_SCOPE;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_PRIVATE_KEY_JSON;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_PROJECT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_SCOPE;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.emptyRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpOAuth2Request;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpOtherPrivateKeyJsonBytes;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpPrivateKeyJsonBytes;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpServiceAccountRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpServiceAccountRequestWithProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AwsAccessKeySecret;
import io.openaev.database.model.AzureServicePrincipalSecret;
import io.openaev.database.model.GcpOAuth2Secret;
import io.openaev.database.model.GcpScopes;
import io.openaev.database.model.GcpServiceAccountSecret;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Secret;
import io.openaev.database.model.UsernamePasswordSecret;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("GCP service account handler tests")
class GcpServiceAccountHandlerTest extends IntegrationTest {

  @Autowired private GcpServiceAccountHandler handler;
  @Autowired private NativeEncryptionService nativeEncryptionService;

  private String decryptedKey(GcpServiceAccountSecret secret) {
    return new String(
        nativeEncryptionService.decrypt(secret.getPrivateKeyJson()), StandardCharsets.UTF_8);
  }

  @Nested
  @DisplayName("supports(Secret)")
  class SupportsSecret {

    @Test
    @DisplayName("given_gcpServiceAccountSecret_should_beSupported")
    void given_gcpServiceAccountSecret_should_beSupported() {
      // Arrange
      Secret secret = new GcpServiceAccountSecret();

      // Act
      boolean supported = handler.supports(secret);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_otherGcpSecretType_should_notBeSupported")
    void given_otherGcpSecretType_should_notBeSupported() {
      // Arrange
      Secret secret = new GcpOAuth2Secret();

      // Act
      boolean supported = handler.supports(secret);

      // Assert
      assertThat(supported).isFalse();
    }

    @Test
    @DisplayName("given_nonGcpSecret_should_notBeSupported")
    void given_nonGcpSecret_should_notBeSupported() {
      // Arrange & Act & Assert
      assertThat(handler.supports(new AzureServicePrincipalSecret())).isFalse();
      assertThat(handler.supports(new AwsAccessKeySecret())).isFalse();
      assertThat(handler.supports(new UsernamePasswordSecret())).isFalse();
      assertThat(handler.supports(new HashSecret())).isFalse();
      assertThat(handler.supports(new Secret())).isFalse();
    }

    @Test
    @DisplayName("given_nullSecret_should_notBeSupported")
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
    @DisplayName("given_gcpServiceAccountReference_should_beSupported")
    void given_gcpServiceAccountReference_should_beSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getGcpServiceAccountReference());

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_gcpOAuth2Reference_should_notBeSupported")
    void given_gcpOAuth2Reference_should_notBeSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getGcpOAuth2Reference());

      // Assert
      assertThat(supported).isFalse();
    }

    @Test
    @DisplayName("given_otherAuthMethodReference_should_notBeSupported")
    void given_otherAuthMethodReference_should_notBeSupported() {
      // Arrange & Act & Assert
      assertThat(handler.supports(getAzureServicePrincipalReference())).isFalse();
      assertThat(handler.supports(getAwsAccessKeyReference())).isFalse();
      assertThat(handler.supports(getUsernamePasswordReference())).isFalse();
      assertThat(handler.supports(getHashReference())).isFalse();
    }

    @Test
    @DisplayName("given_nonCredentialReference_should_notBeSupported")
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
    @DisplayName("given_completeRequest_should_buildGcpServiceAccountSecret")
    void given_completeRequest_should_buildGcpServiceAccountSecret() {
      // Arrange
      SecretStoreRequest request = gcpServiceAccountRequestWithProject();

      // Act
      Secret secret = handler.buildOrUpdate(null, request);

      // Assert
      assertThat(secret).isInstanceOf(GcpServiceAccountSecret.class);
      GcpServiceAccountSecret gcpSecret = (GcpServiceAccountSecret) secret;
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_SCOPE);
      assertThat(gcpSecret.getProjectId()).isEqualTo(GCP_PROJECT_ID);
      assertThat(decryptedKey(gcpSecret)).isEqualTo(GCP_PRIVATE_KEY_JSON);
    }

    @Test
    @DisplayName("given_requestWithoutProject_should_leaveProjectNull")
    void given_requestWithoutProject_should_leaveProjectNull() {
      // Arrange
      SecretStoreRequest request = gcpServiceAccountRequest();

      // Act
      GcpServiceAccountSecret gcpSecret =
          (GcpServiceAccountSecret) handler.buildOrUpdate(null, request);

      // Assert: the project id is optional
      assertThat(gcpSecret.getProjectId()).isNull();
    }

    @Test
    @DisplayName("given_requestWithoutScope_should_fallBackOnDefaultScope")
    void given_requestWithoutScope_should_fallBackOnDefaultScope() {
      // Arrange
      SecretStoreRequest request =
          gcpServiceAccountRequest(null, GCP_PROJECT_ID, gcpPrivateKeyJsonBytes());

      // Act
      GcpServiceAccountSecret gcpSecret =
          (GcpServiceAccountSecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(gcpSecret.getScope()).isEqualTo(GcpScopes.DEFAULT_CLOUD_PLATFORM);
    }

    @Test
    @DisplayName("given_request_should_neverStoreKeyFileInClearText")
    void given_request_should_neverStoreKeyFileInClearText() {
      // Arrange
      SecretStoreRequest request = gcpServiceAccountRequestWithProject();

      // Act
      GcpServiceAccountSecret gcpSecret =
          (GcpServiceAccountSecret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(gcpSecret.getPrivateKeyJson()).isNotEqualTo(gcpPrivateKeyJsonBytes());
      assertThat(new String(gcpSecret.getPrivateKeyJson(), StandardCharsets.UTF_8))
          .doesNotContain("private_key");
    }

    @Test
    @DisplayName("given_request_should_storeScopeAndProjectWithoutEncryption")
    void given_request_should_storeScopeAndProjectWithoutEncryption() {
      // Arrange
      SecretStoreRequest request = gcpServiceAccountRequestWithProject();

      // Act
      GcpServiceAccountSecret gcpSecret =
          (GcpServiceAccountSecret) handler.buildOrUpdate(null, request);

      // Assert: those are plain identifiers, and their columns are sized for clear text
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_SCOPE);
      assertThat(gcpSecret.getProjectId()).isEqualTo(GCP_PROJECT_ID);
    }

    @Test
    @DisplayName("given_existingSecretOfAnotherType_should_buildNewSecret")
    void given_existingSecretOfAnotherType_should_buildNewSecret() {
      // Arrange
      GcpOAuth2Secret existingSecret = new GcpOAuth2Secret();
      existingSecret.setScope("should-be-ignored");

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, gcpServiceAccountRequestWithProject());

      // Assert
      assertThat(secret).isInstanceOf(GcpServiceAccountSecret.class).isNotSameAs(existingSecret);
      assertThat(((GcpServiceAccountSecret) secret).getScope()).isEqualTo(GCP_SCOPE);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - update")
  class BuildOrUpdateUpdate {

    private GcpServiceAccountSecret existingSecret() {
      return (GcpServiceAccountSecret)
          handler.buildOrUpdate(null, gcpServiceAccountRequestWithProject());
    }

    @Test
    @DisplayName("given_existingSecret_should_updateItInPlace")
    void given_existingSecret_should_updateItInPlace() {
      // Arrange
      GcpServiceAccountSecret existingSecret = existingSecret();
      SecretStoreRequest request =
          gcpServiceAccountRequest(
              GCP_OTHER_SCOPE, GCP_OTHER_PROJECT_ID, gcpOtherPrivateKeyJsonBytes());

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(secret).isSameAs(existingSecret);
      assertThat(existingSecret.getScope()).isEqualTo(GCP_OTHER_SCOPE);
      assertThat(existingSecret.getProjectId()).isEqualTo(GCP_OTHER_PROJECT_ID);
      assertThat(decryptedKey(existingSecret)).isEqualTo(GCP_OTHER_PRIVATE_KEY_JSON);
    }

    @Test
    @DisplayName("given_updateWithoutKeyFile_should_keepStoredKey")
    void given_updateWithoutKeyFile_should_keepStoredKey() {
      // Arrange: this is the ordinary PUT, where the user edits metadata without rotating the key
      GcpServiceAccountSecret existingSecret = existingSecret();
      byte[] storedCipherText = existingSecret.getPrivateKeyJson();
      SecretStoreRequest request =
          gcpServiceAccountRequest(GCP_OTHER_SCOPE, GCP_OTHER_PROJECT_ID, null);

      // Act
      GcpServiceAccountSecret gcpSecret =
          (GcpServiceAccountSecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_OTHER_SCOPE);
      assertThat(gcpSecret.getProjectId()).isEqualTo(GCP_OTHER_PROJECT_ID);
      assertThat(gcpSecret.getPrivateKeyJson()).isEqualTo(storedCipherText);
      assertThat(decryptedKey(gcpSecret)).isEqualTo(GCP_PRIVATE_KEY_JSON);
    }

    @Test
    @DisplayName("given_newKeyFile_should_rotateStoredKey")
    void given_newKeyFile_should_rotateStoredKey() {
      // Arrange
      GcpServiceAccountSecret existingSecret = existingSecret();
      byte[] previousCipherText = existingSecret.getPrivateKeyJson();
      SecretStoreRequest request =
          gcpServiceAccountRequest(null, null, gcpOtherPrivateKeyJsonBytes());

      // Act
      GcpServiceAccountSecret gcpSecret =
          (GcpServiceAccountSecret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(gcpSecret.getPrivateKeyJson()).isNotEqualTo(previousCipherText);
      assertThat(decryptedKey(gcpSecret)).isEqualTo(GCP_OTHER_PRIVATE_KEY_JSON);
    }

    @Test
    @DisplayName("given_emptyRequestOnCompleteSecret_should_leaveItUntouched")
    void given_emptyRequestOnCompleteSecret_should_leaveItUntouched() {
      // Arrange
      GcpServiceAccountSecret existingSecret = existingSecret();
      byte[] storedCipherText = existingSecret.getPrivateKeyJson();

      // Act
      GcpServiceAccountSecret gcpSecret =
          (GcpServiceAccountSecret) handler.buildOrUpdate(existingSecret, emptyRequest());

      // Assert
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_SCOPE);
      assertThat(gcpSecret.getProjectId()).isEqualTo(GCP_PROJECT_ID);
      assertThat(gcpSecret.getPrivateKeyJson()).isEqualTo(storedCipherText);
    }

    @Test
    @DisplayName("given_updateWithoutScope_should_keepStoredScope")
    void given_updateWithoutScope_should_keepStoredScope() {
      // Arrange: the default only applies on creation, an update must not silently reset the scope
      GcpServiceAccountSecret existingSecret =
          (GcpServiceAccountSecret)
              handler.buildOrUpdate(
                  null,
                  gcpServiceAccountRequest(
                      GCP_OTHER_SCOPE, GCP_PROJECT_ID, gcpPrivateKeyJsonBytes()));

      // Act
      GcpServiceAccountSecret gcpSecret =
          (GcpServiceAccountSecret) handler.buildOrUpdate(existingSecret, emptyRequest());

      // Assert
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_OTHER_SCOPE);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - validation")
  class BuildOrUpdateValidation {

    @Test
    @DisplayName("given_creationWithoutKeyFile_should_throw")
    void given_creationWithoutKeyFile_should_throw() {
      // Arrange
      SecretStoreRequest request = gcpServiceAccountRequest(GCP_SCOPE, GCP_PROJECT_ID, null);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("given_emptyRequestAndNoExistingSecret_should_throw")
    void given_emptyRequestAndNoExistingSecret_should_throw() {
      // Arrange
      SecretStoreRequest request = emptyRequest();

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("given_requestForAnotherAuthMethod_should_throw")
    void given_requestForAnotherAuthMethod_should_throw() {
      // Arrange: an OAuth2 request carries a scope but no key file
      SecretStoreRequest request = gcpOAuth2Request();

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
    @DisplayName("given_gcpServiceAccountSecret_should_exposeNonSensitiveIdentifiers")
    void given_gcpServiceAccountSecret_should_exposeNonSensitiveIdentifiers() {
      // Arrange
      GcpServiceAccountSecret secret =
          (GcpServiceAccountSecret)
              handler.buildOrUpdate(null, gcpServiceAccountRequestWithProject());

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.gcpScope()).isEqualTo(GCP_SCOPE);
      assertThat(metadata.gcpProjectId()).isEqualTo(GCP_PROJECT_ID);
      assertThat(metadata.gcpPrivateKeyDefined()).isTrue();
      assertThat(metadata.username()).isNull();
      assertThat(metadata.hashAlgorithm()).isNull();
      assertThat(metadata.awsDefaultRegion()).isNull();
      assertThat(metadata.awsAccessKeyId()).isNull();
      assertThat(metadata.awsRoleArn()).isNull();
      assertThat(metadata.awsSourceIdentityType()).isNull();
      assertThat(metadata.awsSourceProfileAccessKeyId()).isNull();
      assertThat(metadata.azureEnvironment()).isNull();
      assertThat(metadata.azureClientId()).isNull();
      assertThat(metadata.azureTenantId()).isNull();
      assertThat(metadata.azureSubscriptionId()).isNull();
    }

    @Test
    @DisplayName("given_gcpServiceAccountSecret_should_neverExposeKeyMaterial")
    void given_gcpServiceAccountSecret_should_neverExposeKeyMaterial() {
      // Arrange
      GcpServiceAccountSecret secret =
          (GcpServiceAccountSecret)
              handler.buildOrUpdate(null, gcpServiceAccountRequestWithProject());

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert: neither the plaintext key nor its cipher text may reach the API output
      assertThat(metadata.toString())
          .doesNotContain(GCP_PRIVATE_KEY_JSON)
          .doesNotContain("private_key")
          .doesNotContain(new String(secret.getPrivateKeyJson(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("given_secretWithoutKey_should_reportKeyAsNotDefined")
    void given_secretWithoutKey_should_reportKeyAsNotDefined() {
      // Arrange: only reachable on a secret built outside the handler, which always requires a key
      GcpServiceAccountSecret secret = new GcpServiceAccountSecret();
      secret.setScope(GCP_SCOPE);

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.gcpPrivateKeyDefined()).isFalse();
    }

    @Test
    @DisplayName("given_secretOfAnotherType_should_throw")
    void given_secretOfAnotherType_should_throw() {
      // Arrange
      Secret secret = new GcpOAuth2Secret();

      // Act & Assert
      assertThatThrownBy(() -> handler.toMetadata(secret))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(TYPE_MISMATCH_MESSAGE);
    }

    @Test
    @DisplayName("given_nullSecret_should_throw")
    void given_nullSecret_should_throw() {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> handler.toMetadata(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(TYPE_MISMATCH_MESSAGE);
    }
  }

  @Nested
  @DisplayName("validateConnection")
  class ValidateConnection {

    @Test
    @DisplayName("given_secretWithUnparsableKey_should_returnUnknownInvalidConfiguration")
    void given_secretWithUnparsableKey_should_returnUnknownInvalidConfiguration() {
      // Arrange: the fixture key is not a real service account file, so the probe stops on a
      // stored-configuration problem before any network call. The full mapping is covered by
      // GcpCredentialConnectivityCheckTest, and the delegation by
      // SecretHandlerValidateConnectionTest.
      GcpServiceAccountSecret secret =
          (GcpServiceAccountSecret)
              handler.buildOrUpdate(null, gcpServiceAccountRequestWithProject());

      // Act
      SecretConnectionResult result = handler.validateConnection(secret);

      // Assert
      assertThat(result.outcome()).isEqualTo(SecretConnectionResult.OUTCOME.UNKNOWN);
      assertThat(result.detail()).isEqualTo(INVALID_CONFIGURATION);
    }

    @Test
    @DisplayName("given_secretOfAnotherType_should_throw")
    void given_validateConnectionWithAnotherType_should_throw() {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> handler.validateConnection(new GcpOAuth2Secret()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(TYPE_MISMATCH_MESSAGE);
    }
  }
}
