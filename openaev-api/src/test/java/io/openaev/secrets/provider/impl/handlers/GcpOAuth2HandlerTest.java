package io.openaev.secrets.provider.impl.handlers;

import static io.openaev.database.model.SecretReference.SECRET_STATUS.FORMAT_ERROR;
import static io.openaev.secrets.provider.impl.handlers.GcpOAuth2Handler.MANDATORY_FIELDS_MESSAGE;
import static io.openaev.secrets.provider.impl.handlers.GcpOAuth2Handler.TYPE_MISMATCH_MESSAGE;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAwsAccessKeyReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getAzureServicePrincipalReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getGcpOAuth2Reference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getGcpServiceAccountReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getHashReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getNonCredentialReference;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getUsernamePasswordReference;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_REFRESH_TOKEN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_OAUTH_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_OAUTH_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_OAUTH_REFRESH_TOKEN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_PROJECT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_SCOPE;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_PROJECT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_SCOPE;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.emptyRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpOAuth2Request;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpOAuth2RequestWithProject;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("GCP OAuth2 handler tests")
class GcpOAuth2HandlerTest extends IntegrationTest {

  @Autowired private GcpOAuth2Handler handler;
  @Autowired private NativeEncryptionService nativeEncryptionService;

  private GcpOAuth2Secret completeSecret() {
    return (GcpOAuth2Secret) handler.buildOrUpdate(null, gcpOAuth2RequestWithProject());
  }

  @Nested
  @DisplayName("supports(Secret)")
  class SupportsSecret {

    @Test
    @DisplayName("given_gcpOAuth2Secret_should_beSupported")
    void given_gcpOAuth2Secret_should_beSupported() {
      // Arrange
      Secret secret = new GcpOAuth2Secret();

      // Act
      boolean supported = handler.supports(secret);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_otherGcpSecretType_should_notBeSupported")
    void given_otherGcpSecretType_should_notBeSupported() {
      // Arrange
      Secret secret = new GcpServiceAccountSecret();

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
    @DisplayName("given_gcpOAuth2Reference_should_beSupported")
    void given_gcpOAuth2Reference_should_beSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getGcpOAuth2Reference());

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_gcpServiceAccountReference_should_notBeSupported")
    void given_gcpServiceAccountReference_should_notBeSupported() {
      // Arrange & Act
      boolean supported = handler.supports(getGcpServiceAccountReference());

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
    @DisplayName("given_completeRequest_should_buildGcpOAuth2Secret")
    void given_completeRequest_should_buildGcpOAuth2Secret() {
      // Arrange
      SecretStoreRequest request = gcpOAuth2RequestWithProject();

      // Act
      Secret secret = handler.buildOrUpdate(null, request);

      // Assert
      assertThat(secret).isInstanceOf(GcpOAuth2Secret.class);
      GcpOAuth2Secret gcpSecret = (GcpOAuth2Secret) secret;
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_SCOPE);
      assertThat(gcpSecret.getProjectId()).isEqualTo(GCP_PROJECT_ID);
      assertThat(gcpSecret.getOauthClientId()).isEqualTo(GCP_OAUTH_CLIENT_ID);
      assertThat(nativeEncryptionService.decrypt(gcpSecret.getOauthClientSecret()))
          .isEqualTo(GCP_OAUTH_CLIENT_SECRET);
      assertThat(nativeEncryptionService.decrypt(gcpSecret.getOauthRefreshToken()))
          .isEqualTo(GCP_OAUTH_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("given_request_should_neverStoreSecretsInClearText")
    void given_request_should_neverStoreSecretsInClearText() {
      // Arrange & Act
      GcpOAuth2Secret gcpSecret = completeSecret();

      // Assert
      assertThat(gcpSecret.getOauthClientSecret()).isNotEqualTo(GCP_OAUTH_CLIENT_SECRET);
      assertThat(gcpSecret.getOauthRefreshToken()).isNotEqualTo(GCP_OAUTH_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("given_request_should_storeIdentifiersWithoutEncryption")
    void given_request_should_storeIdentifiersWithoutEncryption() {
      // Arrange & Act
      GcpOAuth2Secret gcpSecret = completeSecret();

      // Assert: those are plain identifiers, like azure_client_id
      assertThat(gcpSecret.getOauthClientId()).isEqualTo(GCP_OAUTH_CLIENT_ID);
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_SCOPE);
      assertThat(gcpSecret.getProjectId()).isEqualTo(GCP_PROJECT_ID);
    }

    @Test
    @DisplayName("given_requestWithoutProject_should_leaveProjectNull")
    void given_requestWithoutProject_should_leaveProjectNull() {
      // Arrange
      SecretStoreRequest request = gcpOAuth2Request();

      // Act
      GcpOAuth2Secret gcpSecret = (GcpOAuth2Secret) handler.buildOrUpdate(null, request);

      // Assert: the project id is optional
      assertThat(gcpSecret.getProjectId()).isNull();
    }

    @Test
    @DisplayName("given_requestWithoutScope_should_fallBackOnDefaultScope")
    void given_requestWithoutScope_should_fallBackOnDefaultScope() {
      // Arrange
      SecretStoreRequest request =
          gcpOAuth2Request(
              null,
              GCP_PROJECT_ID,
              GCP_OAUTH_CLIENT_ID,
              GCP_OAUTH_CLIENT_SECRET,
              GCP_OAUTH_REFRESH_TOKEN);

      // Act
      GcpOAuth2Secret gcpSecret = (GcpOAuth2Secret) handler.buildOrUpdate(null, request);

      // Assert
      assertThat(gcpSecret.getScope()).isEqualTo(GcpScopes.DEFAULT_CLOUD_PLATFORM);
    }

    @Test
    @DisplayName("given_existingSecretOfAnotherType_should_buildNewSecret")
    void given_existingSecretOfAnotherType_should_buildNewSecret() {
      // Arrange
      GcpServiceAccountSecret existingSecret = new GcpServiceAccountSecret();
      existingSecret.setScope("should-be-ignored");

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, gcpOAuth2RequestWithProject());

      // Assert
      assertThat(secret).isInstanceOf(GcpOAuth2Secret.class).isNotSameAs(existingSecret);
      assertThat(((GcpOAuth2Secret) secret).getScope()).isEqualTo(GCP_SCOPE);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - update")
  class BuildOrUpdateUpdate {

    @Test
    @DisplayName("given_existingSecret_should_updateItInPlace")
    void given_existingSecret_should_updateItInPlace() {
      // Arrange
      GcpOAuth2Secret existingSecret = completeSecret();
      SecretStoreRequest request =
          gcpOAuth2Request(
              GCP_OTHER_SCOPE,
              GCP_OTHER_PROJECT_ID,
              GCP_OTHER_OAUTH_CLIENT_ID,
              GCP_OTHER_OAUTH_CLIENT_SECRET,
              GCP_OTHER_OAUTH_REFRESH_TOKEN);

      // Act
      Secret secret = handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(secret).isSameAs(existingSecret);
      assertThat(existingSecret.getScope()).isEqualTo(GCP_OTHER_SCOPE);
      assertThat(existingSecret.getProjectId()).isEqualTo(GCP_OTHER_PROJECT_ID);
      assertThat(existingSecret.getOauthClientId()).isEqualTo(GCP_OTHER_OAUTH_CLIENT_ID);
      assertThat(nativeEncryptionService.decrypt(existingSecret.getOauthClientSecret()))
          .isEqualTo(GCP_OTHER_OAUTH_CLIENT_SECRET);
      assertThat(nativeEncryptionService.decrypt(existingSecret.getOauthRefreshToken()))
          .isEqualTo(GCP_OTHER_OAUTH_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("given_updateWithoutWriteOnlyFields_should_keepStoredSecrets")
    void given_updateWithoutWriteOnlyFields_should_keepStoredSecrets() {
      // Arrange: this is the ordinary PUT, where the user edits metadata without rotating secrets
      GcpOAuth2Secret existingSecret = completeSecret();
      String storedClientSecret = existingSecret.getOauthClientSecret();
      String storedRefreshToken = existingSecret.getOauthRefreshToken();
      SecretStoreRequest request =
          gcpOAuth2Request(
              GCP_OTHER_SCOPE, GCP_OTHER_PROJECT_ID, GCP_OTHER_OAUTH_CLIENT_ID, null, null);

      // Act
      GcpOAuth2Secret gcpSecret = (GcpOAuth2Secret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_OTHER_SCOPE);
      assertThat(gcpSecret.getProjectId()).isEqualTo(GCP_OTHER_PROJECT_ID);
      assertThat(gcpSecret.getOauthClientId()).isEqualTo(GCP_OTHER_OAUTH_CLIENT_ID);
      assertThat(gcpSecret.getOauthClientSecret()).isEqualTo(storedClientSecret);
      assertThat(gcpSecret.getOauthRefreshToken()).isEqualTo(storedRefreshToken);
      assertThat(nativeEncryptionService.decrypt(gcpSecret.getOauthClientSecret()))
          .isEqualTo(GCP_OAUTH_CLIENT_SECRET);
      assertThat(nativeEncryptionService.decrypt(gcpSecret.getOauthRefreshToken()))
          .isEqualTo(GCP_OAUTH_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("given_newClientSecret_should_rotateStoredClientSecretOnly")
    void given_newClientSecret_should_rotateStoredClientSecretOnly() {
      // Arrange
      GcpOAuth2Secret existingSecret = completeSecret();
      String storedRefreshToken = existingSecret.getOauthRefreshToken();
      SecretStoreRequest request =
          gcpOAuth2Request(null, null, null, GCP_OTHER_OAUTH_CLIENT_SECRET, null);

      // Act
      GcpOAuth2Secret gcpSecret = (GcpOAuth2Secret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(nativeEncryptionService.decrypt(gcpSecret.getOauthClientSecret()))
          .isEqualTo(GCP_OTHER_OAUTH_CLIENT_SECRET);
      assertThat(gcpSecret.getOauthRefreshToken()).isEqualTo(storedRefreshToken);
    }

    @Test
    @DisplayName("given_newRefreshToken_should_rotateStoredRefreshTokenOnly")
    void given_newRefreshToken_should_rotateStoredRefreshTokenOnly() {
      // Arrange
      GcpOAuth2Secret existingSecret = completeSecret();
      String storedClientSecret = existingSecret.getOauthClientSecret();
      SecretStoreRequest request =
          gcpOAuth2Request(null, null, null, null, GCP_OTHER_OAUTH_REFRESH_TOKEN);

      // Act
      GcpOAuth2Secret gcpSecret = (GcpOAuth2Secret) handler.buildOrUpdate(existingSecret, request);

      // Assert
      assertThat(nativeEncryptionService.decrypt(gcpSecret.getOauthRefreshToken()))
          .isEqualTo(GCP_OTHER_OAUTH_REFRESH_TOKEN);
      assertThat(gcpSecret.getOauthClientSecret()).isEqualTo(storedClientSecret);
    }

    @Test
    @DisplayName("given_emptyRequestOnCompleteSecret_should_leaveItUntouched")
    void given_emptyRequestOnCompleteSecret_should_leaveItUntouched() {
      // Arrange
      GcpOAuth2Secret existingSecret = completeSecret();
      String storedClientSecret = existingSecret.getOauthClientSecret();
      String storedRefreshToken = existingSecret.getOauthRefreshToken();

      // Act
      GcpOAuth2Secret gcpSecret =
          (GcpOAuth2Secret) handler.buildOrUpdate(existingSecret, emptyRequest());

      // Assert
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_SCOPE);
      assertThat(gcpSecret.getProjectId()).isEqualTo(GCP_PROJECT_ID);
      assertThat(gcpSecret.getOauthClientId()).isEqualTo(GCP_OAUTH_CLIENT_ID);
      assertThat(gcpSecret.getOauthClientSecret()).isEqualTo(storedClientSecret);
      assertThat(gcpSecret.getOauthRefreshToken()).isEqualTo(storedRefreshToken);
    }

    @Test
    @DisplayName("given_updateWithoutScope_should_keepStoredScope")
    void given_updateWithoutScope_should_keepStoredScope() {
      // Arrange: the default only applies on creation, an update must not silently reset the scope
      GcpOAuth2Secret existingSecret =
          (GcpOAuth2Secret)
              handler.buildOrUpdate(
                  null,
                  gcpOAuth2Request(
                      GCP_OTHER_SCOPE,
                      GCP_PROJECT_ID,
                      GCP_OAUTH_CLIENT_ID,
                      GCP_OAUTH_CLIENT_SECRET,
                      GCP_OAUTH_REFRESH_TOKEN));

      // Act
      GcpOAuth2Secret gcpSecret =
          (GcpOAuth2Secret) handler.buildOrUpdate(existingSecret, emptyRequest());

      // Assert
      assertThat(gcpSecret.getScope()).isEqualTo(GCP_OTHER_SCOPE);
    }
  }

  @Nested
  @DisplayName("buildOrUpdate - validation")
  class BuildOrUpdateValidation {

    @Test
    @DisplayName("given_creationWithoutClientId_should_throw")
    void given_creationWithoutClientId_should_throw() {
      // Arrange
      SecretStoreRequest request =
          gcpOAuth2Request(
              GCP_SCOPE, GCP_PROJECT_ID, null, GCP_OAUTH_CLIENT_SECRET, GCP_OAUTH_REFRESH_TOKEN);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("given_creationWithoutClientSecret_should_throw")
    void given_creationWithoutClientSecret_should_throw() {
      // Arrange
      SecretStoreRequest request =
          gcpOAuth2Request(
              GCP_SCOPE, GCP_PROJECT_ID, GCP_OAUTH_CLIENT_ID, null, GCP_OAUTH_REFRESH_TOKEN);

      // Act & Assert
      assertThatThrownBy(() -> handler.buildOrUpdate(null, request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(MANDATORY_FIELDS_MESSAGE);
    }

    @Test
    @DisplayName("given_creationWithoutRefreshToken_should_throw")
    void given_creationWithoutRefreshToken_should_throw() {
      // Arrange
      SecretStoreRequest request =
          gcpOAuth2Request(
              GCP_SCOPE, GCP_PROJECT_ID, GCP_OAUTH_CLIENT_ID, GCP_OAUTH_CLIENT_SECRET, null);

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
      // Arrange: a service account request carries a scope but no OAuth fields
      SecretStoreRequest request = gcpServiceAccountRequestWithProject();

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
    @DisplayName("given_gcpOAuth2Secret_should_exposeNonSensitiveIdentifiers")
    void given_gcpOAuth2Secret_should_exposeNonSensitiveIdentifiers() {
      // Arrange
      GcpOAuth2Secret secret = completeSecret();

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.gcpScope()).isEqualTo(GCP_SCOPE);
      assertThat(metadata.gcpProjectId()).isEqualTo(GCP_PROJECT_ID);
      assertThat(metadata.gcpOauthClientId()).isEqualTo(GCP_OAUTH_CLIENT_ID);
      assertThat(metadata.gcpOauthClientSecretDefined()).isTrue();
      assertThat(metadata.gcpOauthRefreshTokenDefined()).isTrue();
      assertThat(metadata.gcpPrivateKeyDefined()).isFalse();
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
    @DisplayName("given_gcpOAuth2Secret_should_neverExposeClientSecretNorRefreshToken")
    void given_gcpOAuth2Secret_should_neverExposeClientSecretNorRefreshToken() {
      // Arrange
      GcpOAuth2Secret secret = completeSecret();

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert: neither the plaintext values nor their cipher text may reach the API output
      assertThat(metadata.toString())
          .doesNotContain(GCP_OAUTH_CLIENT_SECRET)
          .doesNotContain(GCP_OAUTH_REFRESH_TOKEN)
          .doesNotContain(secret.getOauthClientSecret())
          .doesNotContain(secret.getOauthRefreshToken());
    }

    @Test
    @DisplayName("given_secretWithoutWriteOnlyValues_should_reportThemAsNotDefined")
    void given_secretWithoutWriteOnlyValues_should_reportThemAsNotDefined() {
      // Arrange: only reachable on a secret built outside the handler, which requires both values
      GcpOAuth2Secret secret = new GcpOAuth2Secret();
      secret.setScope(GCP_SCOPE);
      secret.setOauthClientId(GCP_OAUTH_CLIENT_ID);

      // Act
      SecretMetadata metadata = handler.toMetadata(secret);

      // Assert
      assertThat(metadata.gcpOauthClientSecretDefined()).isFalse();
      assertThat(metadata.gcpOauthRefreshTokenDefined()).isFalse();
    }

    @Test
    @DisplayName("given_secretOfAnotherType_should_throw")
    void given_secretOfAnotherType_should_throw() {
      // Arrange
      Secret secret = new GcpServiceAccountSecret();

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
    @DisplayName("given_secretWithoutScope_should_returnFormatError")
    void given_secretWithoutScope_should_returnFormatError() {
      // Arrange: a broken stored configuration is caught before any network call, so this test
      // exercises the real wiring without reaching Google. The full mapping is covered by
      // GcpCredentialConnectivityCheckTest, and the delegation by
      // SecretHandlerValidateConnectionTest.
      GcpOAuth2Secret secret = completeSecret();
      secret.setScope(null);

      // Act
      SecretConnectionResult result = handler.validateConnection(secret);

      // Assert
      assertThat(result.status()).isEqualTo(FORMAT_ERROR);
    }

    @Test
    @DisplayName("given_secretOfAnotherType_should_throw")
    void given_secretOfAnotherType_should_throw() {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> handler.validateConnection(new GcpServiceAccountSecret()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(TYPE_MISMATCH_MESSAGE);
    }
  }
}
