package io.openaev.secrets.provider.impl.handlers;

import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_ACCOUNT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_ENDPOINT_URL;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_OTHER_ACCOUNT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_OTHER_ENDPOINT_URL;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_AUTHORITY_HOST_URI;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_OTHER_AUTHORITY_HOST_URI;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_CUSTOM_IAM_URI;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_CUSTOM_OAUTH_ACCESS_TOKEN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_CUSTOM_PROJECTS_URI;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_CUSTOM_RESOURCE_MANAGER_URI;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_CUSTOM_TOKEN_URI;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_CUSTOM_OAUTH_ACCESS_TOKEN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_CUSTOM_PROJECTS_URI;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_CUSTOM_RESOURCE_MANAGER_URI;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OTHER_CUSTOM_TOKEN_URI;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAccessKeyRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAccessKeyRequestWithOverrides;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAssumeRoleInstanceDefaultRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.awsAssumeRoleRequestWithOverrides;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureManagedIdentityRequestWithOverrides;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureOtherRequestsCertificateBytes;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureRequestsCertificateBytes;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureServicePrincipalRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureServicePrincipalRequestWithOverrides;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.azureUserAssignedManagedIdentityRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpOAuth2Request;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpOAuth2RequestWithOverrides;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpServiceAccountRequest;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpServiceAccountRequestWithOverrides;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AwsAccessKeySecret;
import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AzureManagedIdentitySecret;
import io.openaev.database.model.AzureServicePrincipalSecret;
import io.openaev.database.model.GcpOAuth2Secret;
import io.openaev.database.model.GcpServiceAccountSecret;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretResolvedValue;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Covers the optional sovereign / mocked cloud fields of every cloud handler: storage, "null =
 * untouched, blank = clear", encryption of the sensitive ones, and what the metadata and the
 * resolved value expose.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class SovereignCloudOverridesHandlerTest extends IntegrationTest {

  @Autowired private AwsAccessKeyHandler awsAccessKeyHandler;
  @Autowired private AwsAssumeRoleHandler awsAssumeRoleHandler;
  @Autowired private GcpOAuth2Handler gcpOAuth2Handler;
  @Autowired private GcpServiceAccountHandler gcpServiceAccountHandler;
  @Autowired private AzureServicePrincipalHandler azureServicePrincipalHandler;
  @Autowired private AzureManagedIdentityHandler azureManagedIdentityHandler;
  @Autowired private NativeEncryptionService nativeEncryptionService;

  @Nested
  @DisplayName("AWS access key")
  class AwsAccessKey {

    @Test
    @DisplayName("Given overrides, should store the account id and the endpoint url")
    void given_overrides_should_storeThem() {
      // Arrange
      SecretStoreRequest request = awsAccessKeyRequestWithOverrides();

      // Act
      AwsAccessKeySecret secret =
          (AwsAccessKeySecret) awsAccessKeyHandler.buildOrUpdate(null, request);

      // Assert
      assertThat(secret.getAwsAccountId()).isEqualTo(AWS_ACCOUNT_ID);
      assertThat(secret.getAwsEndpointUrl()).isEqualTo(AWS_ENDPOINT_URL);
    }

    @Test
    @DisplayName("Given no override, should leave both fields empty")
    void given_noOverride_should_leaveThemEmpty() {
      // Act
      AwsAccessKeySecret secret =
          (AwsAccessKeySecret) awsAccessKeyHandler.buildOrUpdate(null, awsAccessKeyRequest());

      // Assert
      assertThat(secret.getAwsAccountId()).isNull();
      assertThat(secret.getAwsEndpointUrl()).isNull();
    }

    @Test
    @DisplayName("Given an update without the fields, should keep the stored values")
    void given_updateWithoutFields_should_keepStoredValues() {
      // Arrange
      AwsAccessKeySecret existing =
          (AwsAccessKeySecret)
              awsAccessKeyHandler.buildOrUpdate(null, awsAccessKeyRequestWithOverrides());

      // Act
      AwsAccessKeySecret updated =
          (AwsAccessKeySecret) awsAccessKeyHandler.buildOrUpdate(existing, awsAccessKeyRequest());

      // Assert
      assertThat(updated.getAwsAccountId()).isEqualTo(AWS_ACCOUNT_ID);
      assertThat(updated.getAwsEndpointUrl()).isEqualTo(AWS_ENDPOINT_URL);
    }

    @Test
    @DisplayName("Given new values, should replace the stored ones")
    void given_newValues_should_replaceStoredOnes() {
      // Arrange
      AwsAccessKeySecret existing =
          (AwsAccessKeySecret)
              awsAccessKeyHandler.buildOrUpdate(null, awsAccessKeyRequestWithOverrides());
      SecretStoreRequest request =
          awsAccessKeyRequest().toBuilder()
              .awsAccountId(AWS_OTHER_ACCOUNT_ID)
              .awsEndpointUrl(AWS_OTHER_ENDPOINT_URL)
              .build();

      // Act
      AwsAccessKeySecret updated =
          (AwsAccessKeySecret) awsAccessKeyHandler.buildOrUpdate(existing, request);

      // Assert
      assertThat(updated.getAwsAccountId()).isEqualTo(AWS_OTHER_ACCOUNT_ID);
      assertThat(updated.getAwsEndpointUrl()).isEqualTo(AWS_OTHER_ENDPOINT_URL);
    }

    @Test
    @DisplayName("Given blank values, should clear the stored ones")
    void given_blankValues_should_clearStoredOnes() {
      // Arrange
      AwsAccessKeySecret existing =
          (AwsAccessKeySecret)
              awsAccessKeyHandler.buildOrUpdate(null, awsAccessKeyRequestWithOverrides());
      SecretStoreRequest request =
          awsAccessKeyRequest().toBuilder().awsAccountId("").awsEndpointUrl("  ").build();

      // Act
      AwsAccessKeySecret updated =
          (AwsAccessKeySecret) awsAccessKeyHandler.buildOrUpdate(existing, request);

      // Assert
      assertThat(updated.getAwsAccountId()).isNull();
      assertThat(updated.getAwsEndpointUrl()).isNull();
    }

    @Test
    @DisplayName(
        "Given a stored secret, should expose the values in the metadata and the resolved value")
    void given_storedSecret_should_exposeValues() {
      // Arrange
      AwsAccessKeySecret secret =
          (AwsAccessKeySecret)
              awsAccessKeyHandler.buildOrUpdate(null, awsAccessKeyRequestWithOverrides());

      // Act
      SecretMetadata metadata = awsAccessKeyHandler.toMetadata(secret);
      SecretResolvedValue.Value.AwsAccessKey resolved =
          (SecretResolvedValue.Value.AwsAccessKey)
              awsAccessKeyHandler.toResolvedValue(secret).value();

      // Assert
      assertThat(metadata.awsAccountId()).isEqualTo(AWS_ACCOUNT_ID);
      assertThat(metadata.awsEndpointUrl()).isEqualTo(AWS_ENDPOINT_URL);
      assertThat(resolved.awsAccountId()).isEqualTo(AWS_ACCOUNT_ID);
      assertThat(resolved.awsEndpointUrl()).isEqualTo(AWS_ENDPOINT_URL);
    }
  }

  @Nested
  @DisplayName("AWS assume role")
  class AwsAssumeRole {

    @Test
    @DisplayName("Given overrides, should store and expose the account id and the endpoint url")
    void given_overrides_should_storeAndExposeThem() {
      // Act
      AwsAssumeRoleSecret secret =
          (AwsAssumeRoleSecret)
              awsAssumeRoleHandler.buildOrUpdate(null, awsAssumeRoleRequestWithOverrides());
      SecretMetadata metadata = awsAssumeRoleHandler.toMetadata(secret);
      SecretResolvedValue.Value.AwsAssumeRole resolved =
          (SecretResolvedValue.Value.AwsAssumeRole)
              awsAssumeRoleHandler.toResolvedValue(secret).value();

      // Assert
      assertThat(secret.getAwsAccountId()).isEqualTo(AWS_ACCOUNT_ID);
      assertThat(secret.getAwsEndpointUrl()).isEqualTo(AWS_ENDPOINT_URL);
      assertThat(metadata.awsAccountId()).isEqualTo(AWS_ACCOUNT_ID);
      assertThat(metadata.awsEndpointUrl()).isEqualTo(AWS_ENDPOINT_URL);
      assertThat(resolved.awsAccountId()).isEqualTo(AWS_ACCOUNT_ID);
      assertThat(resolved.awsEndpointUrl()).isEqualTo(AWS_ENDPOINT_URL);
    }

    @Test
    @DisplayName("Given an update without the fields, should keep the stored values")
    void given_updateWithoutFields_should_keepStoredValues() {
      // Arrange
      AwsAssumeRoleSecret existing =
          (AwsAssumeRoleSecret)
              awsAssumeRoleHandler.buildOrUpdate(null, awsAssumeRoleRequestWithOverrides());

      // Act
      AwsAssumeRoleSecret updated =
          (AwsAssumeRoleSecret)
              awsAssumeRoleHandler.buildOrUpdate(existing, awsAssumeRoleInstanceDefaultRequest());

      // Assert
      assertThat(updated.getAwsAccountId()).isEqualTo(AWS_ACCOUNT_ID);
      assertThat(updated.getAwsEndpointUrl()).isEqualTo(AWS_ENDPOINT_URL);
    }

    @Test
    @DisplayName("Given blank values, should clear the stored ones")
    void given_blankValues_should_clearStoredOnes() {
      // Arrange
      AwsAssumeRoleSecret existing =
          (AwsAssumeRoleSecret)
              awsAssumeRoleHandler.buildOrUpdate(null, awsAssumeRoleRequestWithOverrides());
      SecretStoreRequest request =
          awsAssumeRoleInstanceDefaultRequest().toBuilder()
              .awsAccountId("")
              .awsEndpointUrl("")
              .build();

      // Act
      AwsAssumeRoleSecret updated =
          (AwsAssumeRoleSecret) awsAssumeRoleHandler.buildOrUpdate(existing, request);

      // Assert
      assertThat(updated.getAwsAccountId()).isNull();
      assertThat(updated.getAwsEndpointUrl()).isNull();
    }
  }

  @Nested
  @DisplayName("GCP OAuth 2.0")
  class GcpOAuth2 {

    @Test
    @DisplayName("Given overrides, should store the uris and encrypt the access token")
    void given_overrides_should_storeUrisAndEncryptToken() {
      // Act
      GcpOAuth2Secret secret =
          (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(null, gcpOAuth2RequestWithOverrides());

      // Assert
      assertThat(secret.getCustomTokenUri()).isEqualTo(GCP_CUSTOM_TOKEN_URI);
      assertThat(secret.getCustomProjectsUri()).isEqualTo(GCP_CUSTOM_PROJECTS_URI);
      assertThat(secret.getCustomIamUri()).isEqualTo(GCP_CUSTOM_IAM_URI);
      assertThat(secret.getCustomOauthAccessToken())
          .isNotNull()
          .isNotEqualTo(GCP_CUSTOM_OAUTH_ACCESS_TOKEN);
      assertThat(nativeEncryptionService.decrypt(secret.getCustomOauthAccessToken()))
          .isEqualTo(GCP_CUSTOM_OAUTH_ACCESS_TOKEN);
    }

    @Test
    @DisplayName("Given an update without the fields, should keep the stored values")
    void given_updateWithoutFields_should_keepStoredValues() {
      // Arrange
      GcpOAuth2Secret existing =
          (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(null, gcpOAuth2RequestWithOverrides());
      String storedToken = existing.getCustomOauthAccessToken();

      // Act
      GcpOAuth2Secret updated =
          (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(existing, gcpOAuth2Request());

      // Assert
      assertThat(updated.getCustomTokenUri()).isEqualTo(GCP_CUSTOM_TOKEN_URI);
      assertThat(updated.getCustomProjectsUri()).isEqualTo(GCP_CUSTOM_PROJECTS_URI);
      assertThat(updated.getCustomIamUri()).isEqualTo(GCP_CUSTOM_IAM_URI);
      assertThat(updated.getCustomOauthAccessToken()).isEqualTo(storedToken);
    }

    @Test
    @DisplayName("Given a new access token, should replace the stored one")
    void given_newAccessToken_should_replaceStoredOne() {
      // Arrange
      GcpOAuth2Secret existing =
          (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(null, gcpOAuth2RequestWithOverrides());
      SecretStoreRequest request =
          gcpOAuth2Request().toBuilder()
              .gcpCustomOauthAccessToken(GCP_OTHER_CUSTOM_OAUTH_ACCESS_TOKEN)
              .gcpCustomTokenUri(GCP_OTHER_CUSTOM_TOKEN_URI)
              .gcpCustomProjectsUri(GCP_OTHER_CUSTOM_PROJECTS_URI)
              .build();

      // Act
      GcpOAuth2Secret updated = (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(existing, request);

      // Assert
      assertThat(nativeEncryptionService.decrypt(updated.getCustomOauthAccessToken()))
          .isEqualTo(GCP_OTHER_CUSTOM_OAUTH_ACCESS_TOKEN);
      assertThat(updated.getCustomTokenUri()).isEqualTo(GCP_OTHER_CUSTOM_TOKEN_URI);
      assertThat(updated.getCustomProjectsUri()).isEqualTo(GCP_OTHER_CUSTOM_PROJECTS_URI);
    }

    @Test
    @DisplayName("Given blank values, should clear the stored ones")
    void given_blankValues_should_clearStoredOnes() {
      // Arrange
      GcpOAuth2Secret existing =
          (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(null, gcpOAuth2RequestWithOverrides());
      SecretStoreRequest request =
          gcpOAuth2Request().toBuilder()
              .gcpCustomTokenUri("")
              .gcpCustomProjectsUri("")
              .gcpCustomIamUri("")
              .gcpCustomOauthAccessToken("")
              .build();

      // Act
      GcpOAuth2Secret updated = (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(existing, request);

      // Assert
      assertThat(updated.getCustomTokenUri()).isNull();
      assertThat(updated.getCustomProjectsUri()).isNull();
      assertThat(updated.getCustomIamUri()).isNull();
      assertThat(updated.getCustomOauthAccessToken()).isNull();
    }

    @Test
    @DisplayName("Given a stored access token, the metadata only says it is defined")
    void given_storedAccessToken_should_exposeOnlyABooleanInMetadata() {
      // Arrange
      GcpOAuth2Secret secret =
          (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(null, gcpOAuth2RequestWithOverrides());

      // Act
      SecretMetadata metadata = gcpOAuth2Handler.toMetadata(secret);

      // Assert
      assertThat(metadata.gcpCustomTokenUri()).isEqualTo(GCP_CUSTOM_TOKEN_URI);
      assertThat(metadata.gcpCustomProjectsUri()).isEqualTo(GCP_CUSTOM_PROJECTS_URI);
      assertThat(metadata.gcpCustomIamUri()).isEqualTo(GCP_CUSTOM_IAM_URI);
      assertThat(metadata.gcpCustomOauthAccessTokenDefined()).isTrue();
      assertThat(metadata.toString()).doesNotContain(GCP_CUSTOM_OAUTH_ACCESS_TOKEN);
    }

    @Test
    @DisplayName("Given no access token, the metadata says it is not defined")
    void given_noAccessToken_should_exposeUndefined() {
      // Arrange
      GcpOAuth2Secret secret =
          (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(null, gcpOAuth2Request());

      // Act
      SecretMetadata metadata = gcpOAuth2Handler.toMetadata(secret);

      // Assert
      assertThat(metadata.gcpCustomOauthAccessTokenDefined()).isFalse();
    }

    @Test
    @DisplayName("Given a stored secret, the resolved value carries the decrypted access token")
    void given_storedSecret_should_resolveDecryptedAccessToken() {
      // Arrange
      GcpOAuth2Secret secret =
          (GcpOAuth2Secret) gcpOAuth2Handler.buildOrUpdate(null, gcpOAuth2RequestWithOverrides());

      // Act
      SecretResolvedValue.Value.GcpOAuth2 resolved =
          (SecretResolvedValue.Value.GcpOAuth2) gcpOAuth2Handler.toResolvedValue(secret).value();

      // Assert
      assertThat(resolved.gcpCustomTokenUri()).isEqualTo(GCP_CUSTOM_TOKEN_URI);
      assertThat(resolved.gcpCustomIamUri()).isEqualTo(GCP_CUSTOM_IAM_URI);
      assertThat(resolved.gcpCustomOauthAccessToken()).isEqualTo(GCP_CUSTOM_OAUTH_ACCESS_TOKEN);
    }
  }

  @Nested
  @DisplayName("GCP service account")
  class GcpServiceAccount {

    @Test
    @DisplayName("Given overrides, should store and expose the resource manager and projects uris")
    void given_overrides_should_storeAndExposeThem() {
      // Act
      GcpServiceAccountSecret secret =
          (GcpServiceAccountSecret)
              gcpServiceAccountHandler.buildOrUpdate(null, gcpServiceAccountRequestWithOverrides());
      SecretMetadata metadata = gcpServiceAccountHandler.toMetadata(secret);
      SecretResolvedValue.Value.GcpServiceAccount resolved =
          (SecretResolvedValue.Value.GcpServiceAccount)
              gcpServiceAccountHandler.toResolvedValue(secret).value();

      // Assert
      assertThat(secret.getCustomResourceManagerUri()).isEqualTo(GCP_CUSTOM_RESOURCE_MANAGER_URI);
      assertThat(secret.getCustomProjectsUri()).isEqualTo(GCP_CUSTOM_PROJECTS_URI);
      assertThat(metadata.gcpCustomResourceManagerUri()).isEqualTo(GCP_CUSTOM_RESOURCE_MANAGER_URI);
      assertThat(metadata.gcpCustomProjectsUri()).isEqualTo(GCP_CUSTOM_PROJECTS_URI);
      assertThat(resolved.gcpCustomResourceManagerUri()).isEqualTo(GCP_CUSTOM_RESOURCE_MANAGER_URI);
    }

    @Test
    @DisplayName("Given an update without the fields, should keep the stored values")
    void given_updateWithoutFields_should_keepStoredValues() {
      // Arrange
      GcpServiceAccountSecret existing =
          (GcpServiceAccountSecret)
              gcpServiceAccountHandler.buildOrUpdate(null, gcpServiceAccountRequestWithOverrides());

      // Act
      GcpServiceAccountSecret updated =
          (GcpServiceAccountSecret)
              gcpServiceAccountHandler.buildOrUpdate(existing, gcpServiceAccountRequest());

      // Assert
      assertThat(updated.getCustomResourceManagerUri()).isEqualTo(GCP_CUSTOM_RESOURCE_MANAGER_URI);
      assertThat(updated.getCustomProjectsUri()).isEqualTo(GCP_CUSTOM_PROJECTS_URI);
    }

    @Test
    @DisplayName("Given new and blank values, should replace and clear the stored ones")
    void given_newAndBlankValues_should_replaceAndClear() {
      // Arrange
      GcpServiceAccountSecret existing =
          (GcpServiceAccountSecret)
              gcpServiceAccountHandler.buildOrUpdate(null, gcpServiceAccountRequestWithOverrides());
      SecretStoreRequest request =
          gcpServiceAccountRequest().toBuilder()
              .gcpCustomResourceManagerUri(GCP_OTHER_CUSTOM_RESOURCE_MANAGER_URI)
              .gcpCustomProjectsUri("")
              .build();

      // Act
      GcpServiceAccountSecret updated =
          (GcpServiceAccountSecret) gcpServiceAccountHandler.buildOrUpdate(existing, request);

      // Assert
      assertThat(updated.getCustomResourceManagerUri())
          .isEqualTo(GCP_OTHER_CUSTOM_RESOURCE_MANAGER_URI);
      assertThat(updated.getCustomProjectsUri()).isNull();
    }
  }

  @Nested
  @DisplayName("Azure service principal")
  class AzureServicePrincipal {

    @Test
    @DisplayName("Given overrides, should store the authority host and encrypt the certificate")
    void given_overrides_should_storeAuthorityHostAndEncryptCertificate() {
      // Act
      AzureServicePrincipalSecret secret =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(
                  null, azureServicePrincipalRequestWithOverrides());

      // Assert
      assertThat(secret.getAzureAuthorityHostUri()).isEqualTo(AZURE_AUTHORITY_HOST_URI);
      assertThat(secret.getAzureRequestsCertificate())
          .isNotNull()
          .isNotEqualTo(azureRequestsCertificateBytes());
      assertThat(nativeEncryptionService.decrypt(secret.getAzureRequestsCertificate()))
          .isEqualTo(azureRequestsCertificateBytes());
    }

    @Test
    @DisplayName("Given an update without the file part, should keep the stored certificate")
    void given_updateWithoutFilePart_should_keepStoredCertificate() {
      // Arrange
      AzureServicePrincipalSecret existing =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(
                  null, azureServicePrincipalRequestWithOverrides());
      byte[] storedCertificate = existing.getAzureRequestsCertificate();

      // Act
      AzureServicePrincipalSecret updated =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(existing, azureServicePrincipalRequest());

      // Assert
      assertThat(updated.getAzureRequestsCertificate()).isEqualTo(storedCertificate);
      assertThat(updated.getAzureAuthorityHostUri()).isEqualTo(AZURE_AUTHORITY_HOST_URI);
    }

    @Test
    @DisplayName("Given a new file part, should replace the stored certificate")
    void given_newFilePart_should_replaceStoredCertificate() {
      // Arrange
      AzureServicePrincipalSecret existing =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(
                  null, azureServicePrincipalRequestWithOverrides());
      SecretStoreRequest request =
          azureServicePrincipalRequest().toBuilder()
              .azureRequestsCertificate(azureOtherRequestsCertificateBytes())
              .azureAuthorityHostUri(AZURE_OTHER_AUTHORITY_HOST_URI)
              .build();

      // Act
      AzureServicePrincipalSecret updated =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(existing, request);

      // Assert
      assertThat(nativeEncryptionService.decrypt(updated.getAzureRequestsCertificate()))
          .isEqualTo(azureOtherRequestsCertificateBytes());
      assertThat(updated.getAzureAuthorityHostUri()).isEqualTo(AZURE_OTHER_AUTHORITY_HOST_URI);
    }

    @Test
    @DisplayName("Given a blank authority host, should clear the stored one")
    void given_blankAuthorityHost_should_clearStoredOne() {
      // Arrange
      AzureServicePrincipalSecret existing =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(
                  null, azureServicePrincipalRequestWithOverrides());
      SecretStoreRequest request =
          azureServicePrincipalRequest().toBuilder().azureAuthorityHostUri("").build();

      // Act
      AzureServicePrincipalSecret updated =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(existing, request);

      // Assert
      assertThat(updated.getAzureAuthorityHostUri()).isNull();
    }

    @Test
    @DisplayName("Given a stored certificate, the metadata only says it is defined")
    void given_storedCertificate_should_exposeOnlyABooleanInMetadata() {
      // Arrange
      AzureServicePrincipalSecret secret =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(
                  null, azureServicePrincipalRequestWithOverrides());

      // Act
      SecretMetadata metadata = azureServicePrincipalHandler.toMetadata(secret);

      // Assert
      assertThat(metadata.azureAuthorityHostUri()).isEqualTo(AZURE_AUTHORITY_HOST_URI);
      assertThat(metadata.azureRequestsCertificateDefined()).isTrue();
    }

    @Test
    @DisplayName("Given no certificate, the metadata and the resolved value carry none")
    void given_noCertificate_should_exposeNone() {
      // Arrange
      AzureServicePrincipalSecret secret =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(null, azureServicePrincipalRequest());

      // Act
      SecretMetadata metadata = azureServicePrincipalHandler.toMetadata(secret);
      SecretResolvedValue.Value.AzureServicePrincipal resolved =
          (SecretResolvedValue.Value.AzureServicePrincipal)
              azureServicePrincipalHandler.toResolvedValue(secret).value();

      // Assert
      assertThat(metadata.azureRequestsCertificateDefined()).isFalse();
      assertThat(metadata.azureAuthorityHostUri()).isNull();
      assertThat(resolved.azureRequestsCertificate()).isNull();
      assertThat(resolved.azureAuthorityHostUri()).isNull();
    }

    @Test
    @DisplayName("Given a stored certificate, the resolved value carries the decrypted content")
    void given_storedCertificate_should_resolveDecryptedContent() {
      // Arrange
      AzureServicePrincipalSecret secret =
          (AzureServicePrincipalSecret)
              azureServicePrincipalHandler.buildOrUpdate(
                  null, azureServicePrincipalRequestWithOverrides());

      // Act
      SecretResolvedValue.Value.AzureServicePrincipal resolved =
          (SecretResolvedValue.Value.AzureServicePrincipal)
              azureServicePrincipalHandler.toResolvedValue(secret).value();

      // Assert
      assertThat(resolved.azureAuthorityHostUri()).isEqualTo(AZURE_AUTHORITY_HOST_URI);
      assertThat(resolved.azureRequestsCertificate()).isEqualTo(azureRequestsCertificateBytes());
    }
  }

  @Nested
  @DisplayName("Azure managed identity")
  class AzureManagedIdentity {

    @Test
    @DisplayName("Given overrides, should store the authority host and encrypt the certificate")
    void given_overrides_should_storeAuthorityHostAndEncryptCertificate() {
      // Act
      AzureManagedIdentitySecret secret =
          (AzureManagedIdentitySecret)
              azureManagedIdentityHandler.buildOrUpdate(
                  null, azureManagedIdentityRequestWithOverrides());

      // Assert
      assertThat(secret.getAzureAuthorityHostUri()).isEqualTo(AZURE_AUTHORITY_HOST_URI);
      assertThat(secret.getAzureRequestsCertificate())
          .isNotNull()
          .isNotEqualTo(azureRequestsCertificateBytes());
      assertThat(nativeEncryptionService.decrypt(secret.getAzureRequestsCertificate()))
          .isEqualTo(azureRequestsCertificateBytes());
    }

    @Test
    @DisplayName("Given an update without the file part, should keep the stored certificate")
    void given_updateWithoutFilePart_should_keepStoredCertificate() {
      // Arrange
      AzureManagedIdentitySecret existing =
          (AzureManagedIdentitySecret)
              azureManagedIdentityHandler.buildOrUpdate(
                  null, azureManagedIdentityRequestWithOverrides());
      byte[] storedCertificate = existing.getAzureRequestsCertificate();

      // Act
      AzureManagedIdentitySecret updated =
          (AzureManagedIdentitySecret)
              azureManagedIdentityHandler.buildOrUpdate(
                  existing, azureUserAssignedManagedIdentityRequest());

      // Assert
      assertThat(updated.getAzureRequestsCertificate()).isEqualTo(storedCertificate);
      assertThat(updated.getAzureAuthorityHostUri()).isEqualTo(AZURE_AUTHORITY_HOST_URI);
    }

    @Test
    @DisplayName("Given a stored certificate, should expose a boolean and the decrypted content")
    void given_storedCertificate_should_exposeBooleanAndResolveContent() {
      // Arrange
      AzureManagedIdentitySecret secret =
          (AzureManagedIdentitySecret)
              azureManagedIdentityHandler.buildOrUpdate(
                  null, azureManagedIdentityRequestWithOverrides());

      // Act
      SecretMetadata metadata = azureManagedIdentityHandler.toMetadata(secret);
      SecretResolvedValue.Value.AzureManagedIdentity resolved =
          (SecretResolvedValue.Value.AzureManagedIdentity)
              azureManagedIdentityHandler.toResolvedValue(secret).value();

      // Assert
      assertThat(metadata.azureAuthorityHostUri()).isEqualTo(AZURE_AUTHORITY_HOST_URI);
      assertThat(metadata.azureRequestsCertificateDefined()).isTrue();
      assertThat(resolved.azureAuthorityHostUri()).isEqualTo(AZURE_AUTHORITY_HOST_URI);
      assertThat(resolved.azureRequestsCertificate()).isEqualTo(azureRequestsCertificateBytes());
    }
  }
}
