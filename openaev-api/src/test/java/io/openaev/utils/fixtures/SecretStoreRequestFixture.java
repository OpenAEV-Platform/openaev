package io.openaev.utils.fixtures;

import io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE;
import io.openaev.database.model.AwsRegion;
import io.openaev.secrets.provider.SecretStoreRequest;
import java.nio.charset.StandardCharsets;

/**
 * Fixtures for {@link SecretStoreRequest} payloads used by the secret handlers.
 *
 * <p>The request is a record with one component per provider field, so every fixture goes through
 * its builder: a test that needs a few extra fields calls {@code toBuilder()} on the fixture.
 */
public class SecretStoreRequestFixture {

  public static final AwsRegion AWS_DEFAULT_REGION = AwsRegion.EU_WEST_3;
  public static final AwsRegion AWS_OTHER_REGION = AwsRegion.US_EAST_1;

  public static final String AWS_ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE";
  public static final String AWS_OTHER_ACCESS_KEY_ID = "AKIAI44QH8DHBEXAMPLE";
  public static final String AWS_SECRET_ACCESS_KEY = "wJalrXUtnFEMI-K7MDENG-bPxRfiCYEXAMPLEKEY";
  public static final String AWS_OTHER_SECRET_ACCESS_KEY =
      "je7MtGbClwBF-2Zp9Utk-h3yCo8nvbEXAMPLEKEY";
  public static final String AWS_SESSION_TOKEN = "FwoGZXIvYXdzEEXAMPLESESSIONTOKEN";

  public static final String AWS_ROLE_ARN = "arn:aws:iam::123456789012:role/openaev-simulation";
  public static final String AWS_OTHER_ROLE_ARN = "arn:aws:iam::123456789012:role/openaev-audit";
  public static final String AWS_EXTERNAL_ID = "openaev-external-id-1234";
  public static final String AWS_SOURCE_PROFILE_ACCESS_KEY_ID = "AKIAIOSFODNN7SOURCE0";
  public static final String AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY =
      "sourceProfileSecret-K7MDENG-bPxRfiCYEXAMPLE";
  public static final String AWS_ACCOUNT_ID = "123456789012";
  public static final String AWS_OTHER_ACCOUNT_ID = "210987654321";
  public static final String AWS_ENDPOINT_URL = "http://localhost:4566";
  public static final String AWS_OTHER_ENDPOINT_URL = "http://sovereign.example:4566";

  public static final String AZURE_ENVIRONMENT = "AzureCloud";
  public static final String AZURE_OTHER_ENVIRONMENT = "AzureUSGovernment";
  public static final String AZURE_CLIENT_ID = "11111111-1111-1111-1111-111111111111";
  public static final String AZURE_OTHER_CLIENT_ID = "22222222-2222-2222-2222-222222222222";
  public static final String AZURE_CLIENT_SECRET = "azureClientSecret-EXAMPLE";
  public static final String AZURE_OTHER_CLIENT_SECRET = "azureOtherClientSecret-EXAMPLE";
  public static final String AZURE_TENANT_ID = "33333333-3333-3333-3333-333333333333";
  public static final String AZURE_OTHER_TENANT_ID = "55555555-5555-5555-5555-555555555555";
  public static final String AZURE_SUBSCRIPTION_ID = "44444444-4444-4444-4444-444444444444";
  public static final String AZURE_AUTHORITY_HOST_URI = "https://localhost:4577";
  public static final String AZURE_OTHER_AUTHORITY_HOST_URI = "https://login.sovereign.example";
  public static final String AZURE_REQUESTS_CERTIFICATE =
      "-----BEGIN CERTIFICATE-----\nMIIBEXAMPLECERTIFICATE\n-----END CERTIFICATE-----\n";
  public static final String AZURE_OTHER_REQUESTS_CERTIFICATE =
      "-----BEGIN CERTIFICATE-----\nMIIBOTHERCERTIFICATE\n-----END CERTIFICATE-----\n";

  public static final String GCP_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
  public static final String GCP_OTHER_SCOPE = "https://www.googleapis.com/auth/compute";
  public static final String GCP_PROJECT_ID = "openaev-simulation";
  public static final String GCP_OTHER_PROJECT_ID = "openaev-audit";
  public static final String GCP_PRIVATE_KEY_JSON =
      "{\"type\":\"service_account\",\"project_id\":\"openaev-simulation\","
          + "\"private_key\":\"-----BEGIN PRIVATE KEY-----EXAMPLE-----END PRIVATE KEY-----\"}";
  public static final String GCP_OTHER_PRIVATE_KEY_JSON =
      "{\"type\":\"service_account\",\"project_id\":\"openaev-audit\","
          + "\"private_key\":\"-----BEGIN PRIVATE KEY-----OTHER-----END PRIVATE KEY-----\"}";
  public static final String GCP_OAUTH_CLIENT_ID = "1234567890-example.apps.googleusercontent.com";
  public static final String GCP_OTHER_OAUTH_CLIENT_ID =
      "0987654321-other.apps.googleusercontent.com";
  public static final String GCP_OAUTH_CLIENT_SECRET = "GOCSPX-gcpClientSecretEXAMPLE";
  public static final String GCP_OTHER_OAUTH_CLIENT_SECRET = "GOCSPX-gcpOtherClientSecretEXAMPLE";
  public static final String GCP_OAUTH_REFRESH_TOKEN = "1//03gcpRefreshTokenEXAMPLE";
  public static final String GCP_OTHER_OAUTH_REFRESH_TOKEN = "1//03gcpOtherRefreshTokenEXAMPLE";
  public static final String GCP_CUSTOM_TOKEN_URI = "http://localhost:4589/token";
  public static final String GCP_OTHER_CUSTOM_TOKEN_URI = "http://sovereign.example/token";
  public static final String GCP_CUSTOM_PROJECTS_URI = "http://localhost:4588/v1/projects/";
  public static final String GCP_OTHER_CUSTOM_PROJECTS_URI =
      "http://sovereign.example/v1/projects/";
  public static final String GCP_CUSTOM_RESOURCE_MANAGER_URI = "http://localhost:4588/token";
  public static final String GCP_OTHER_CUSTOM_RESOURCE_MANAGER_URI =
      "http://sovereign.example/resource-manager";
  public static final String GCP_CUSTOM_IAM_URI = "http://localhost:4588/iam";
  public static final String GCP_CUSTOM_OAUTH_ACCESS_TOKEN = "ya29.customAccessTokenEXAMPLE";
  public static final String GCP_OTHER_CUSTOM_OAUTH_ACCESS_TOKEN =
      "ya29.otherCustomAccessTokenEXAMPLE";

  private SecretStoreRequestFixture() {}

  /** The raw bytes of the reference service account key file. */
  public static byte[] gcpPrivateKeyJsonBytes() {
    return GCP_PRIVATE_KEY_JSON.getBytes(StandardCharsets.UTF_8);
  }

  /** The raw bytes of a second service account key file, used to assert key rotation. */
  public static byte[] gcpOtherPrivateKeyJsonBytes() {
    return GCP_OTHER_PRIVATE_KEY_JSON.getBytes(StandardCharsets.UTF_8);
  }

  /** The raw bytes of the reference Azure requests certificate. */
  public static byte[] azureRequestsCertificateBytes() {
    return AZURE_REQUESTS_CERTIFICATE.getBytes(StandardCharsets.UTF_8);
  }

  /** The raw bytes of a second certificate, used to assert certificate rotation. */
  public static byte[] azureOtherRequestsCertificateBytes() {
    return AZURE_OTHER_REQUESTS_CERTIFICATE.getBytes(StandardCharsets.UTF_8);
  }

  /** A request carrying no value at all — every optional field is {@code null}. */
  public static SecretStoreRequest emptyRequest() {
    return SecretStoreRequest.builder().build();
  }

  // -- AWS_ACCESS_KEY --

  /** A complete, valid AWS_ACCESS_KEY request (without session token). */
  public static SecretStoreRequest awsAccessKeyRequest() {
    return awsAccessKeyRequest(AWS_DEFAULT_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, null);
  }

  /** A complete, valid AWS_ACCESS_KEY request including a temporary session token. */
  public static SecretStoreRequest awsAccessKeyRequestWithSessionToken() {
    return awsAccessKeyRequest(
        AWS_DEFAULT_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN);
  }

  public static SecretStoreRequest awsAccessKeyRequest(
      AwsRegion awsDefaultRegion,
      String awsAccessKeyId,
      String awsSecretAccessKey,
      String awsSessionToken) {
    return SecretStoreRequest.builder()
        .awsDefaultRegion(awsDefaultRegion)
        .awsAccessKeyId(awsAccessKeyId)
        .awsSecretAccessKey(awsSecretAccessKey)
        .awsSessionToken(awsSessionToken)
        .build();
  }

  /** A complete AWS_ACCESS_KEY request targeting a sovereign or mocked AWS. */
  public static SecretStoreRequest awsAccessKeyRequestWithOverrides() {
    return awsAccessKeyRequest().toBuilder()
        .awsAccountId(AWS_ACCOUNT_ID)
        .awsEndpointUrl(AWS_ENDPOINT_URL)
        .build();
  }

  // -- AWS_ASSUME_ROLE --

  /** A complete, valid AWS_ASSUME_ROLE request relying on the instance default identity. */
  public static SecretStoreRequest awsAssumeRoleInstanceDefaultRequest() {
    return awsAssumeRoleRequest(
        AWS_DEFAULT_REGION,
        AWS_ROLE_ARN,
        AWS_EXTERNAL_ID,
        AWS_SOURCE_IDENTITY_TYPE.INSTANCE_DEFAULT,
        null,
        null);
  }

  /** A complete, valid AWS_ASSUME_ROLE request relying on a static source access key. */
  public static SecretStoreRequest awsAssumeRoleStaticAccessKeyRequest() {
    return awsAssumeRoleRequest(
        AWS_DEFAULT_REGION,
        AWS_ROLE_ARN,
        AWS_EXTERNAL_ID,
        AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY,
        AWS_SOURCE_PROFILE_ACCESS_KEY_ID,
        AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
  }

  public static SecretStoreRequest awsAssumeRoleRequest(
      AwsRegion awsDefaultRegion,
      String awsRoleArn,
      String awsExternalId,
      AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
      String awsSourceProfileAccessKeyId,
      String awsSourceProfileSecretAccessKey) {
    return SecretStoreRequest.builder()
        .awsDefaultRegion(awsDefaultRegion)
        .awsRoleArn(awsRoleArn)
        .awsExternalId(awsExternalId)
        .awsSourceIdentityType(awsSourceIdentityType)
        .awsSourceProfileAccessKeyId(awsSourceProfileAccessKeyId)
        .awsSourceProfileSecretAccessKey(awsSourceProfileSecretAccessKey)
        .build();
  }

  /** A complete AWS_ASSUME_ROLE request targeting a sovereign or mocked AWS. */
  public static SecretStoreRequest awsAssumeRoleRequestWithOverrides() {
    return awsAssumeRoleInstanceDefaultRequest().toBuilder()
        .awsAccountId(AWS_ACCOUNT_ID)
        .awsEndpointUrl(AWS_ENDPOINT_URL)
        .build();
  }

  // -- AZURE_SERVICE_PRINCIPAL --

  /** A complete, valid AZURE_SERVICE_PRINCIPAL request (without subscription). */
  public static SecretStoreRequest azureServicePrincipalRequest() {
    return azureServicePrincipalRequest(
        AZURE_ENVIRONMENT, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID, null);
  }

  /** A complete, valid AZURE_SERVICE_PRINCIPAL request including a subscription. */
  public static SecretStoreRequest azureServicePrincipalRequestWithSubscription() {
    return azureServicePrincipalRequest(
        AZURE_ENVIRONMENT,
        AZURE_CLIENT_ID,
        AZURE_CLIENT_SECRET,
        AZURE_TENANT_ID,
        AZURE_SUBSCRIPTION_ID);
  }

  public static SecretStoreRequest azureServicePrincipalRequest(
      String azureEnvironment,
      String azureClientId,
      String azureClientSecret,
      String azureTenantId,
      String azureSubscriptionId) {
    return SecretStoreRequest.builder()
        .azureEnvironment(azureEnvironment)
        .azureClientId(azureClientId)
        .azureClientSecret(azureClientSecret)
        .azureTenantId(azureTenantId)
        .azureSubscriptionId(azureSubscriptionId)
        .build();
  }

  /** A complete AZURE_SERVICE_PRINCIPAL request targeting a sovereign or mocked Azure. */
  public static SecretStoreRequest azureServicePrincipalRequestWithOverrides() {
    return azureServicePrincipalRequest().toBuilder()
        .azureAuthorityHostUri(AZURE_AUTHORITY_HOST_URI)
        .azureRequestsCertificate(azureRequestsCertificateBytes())
        .build();
  }

  // -- AZURE_MANAGED_IDENTITY --

  /** A valid AZURE_MANAGED_IDENTITY request relying on the system-assigned identity. */
  public static SecretStoreRequest azureSystemAssignedManagedIdentityRequest() {
    return azureManagedIdentityRequest(AZURE_ENVIRONMENT, null, null);
  }

  /** A valid AZURE_MANAGED_IDENTITY request relying on a user-assigned identity. */
  public static SecretStoreRequest azureUserAssignedManagedIdentityRequest() {
    return azureManagedIdentityRequest(AZURE_ENVIRONMENT, AZURE_CLIENT_ID, AZURE_SUBSCRIPTION_ID);
  }

  public static SecretStoreRequest azureManagedIdentityRequest(
      String azureEnvironment, String azureClientId, String azureSubscriptionId) {
    return SecretStoreRequest.builder()
        .azureEnvironment(azureEnvironment)
        .azureClientId(azureClientId)
        .azureSubscriptionId(azureSubscriptionId)
        .build();
  }

  /** A valid AZURE_MANAGED_IDENTITY request targeting a sovereign or mocked Azure. */
  public static SecretStoreRequest azureManagedIdentityRequestWithOverrides() {
    return azureUserAssignedManagedIdentityRequest().toBuilder()
        .azureAuthorityHostUri(AZURE_AUTHORITY_HOST_URI)
        .azureRequestsCertificate(azureRequestsCertificateBytes())
        .build();
  }

  // -- GCP_SERVICE_ACCOUNT --

  /** A complete, valid GCP_SERVICE_ACCOUNT request (without project). */
  public static SecretStoreRequest gcpServiceAccountRequest() {
    return gcpServiceAccountRequest(GCP_SCOPE, null, gcpPrivateKeyJsonBytes());
  }

  /** A complete, valid GCP_SERVICE_ACCOUNT request including a project. */
  public static SecretStoreRequest gcpServiceAccountRequestWithProject() {
    return gcpServiceAccountRequest(GCP_SCOPE, GCP_PROJECT_ID, gcpPrivateKeyJsonBytes());
  }

  public static SecretStoreRequest gcpServiceAccountRequest(
      String gcpScope, String gcpProjectId, byte[] gcpPrivateKeyJson) {
    return SecretStoreRequest.builder()
        .gcpScope(gcpScope)
        .gcpProjectId(gcpProjectId)
        .gcpPrivateKeyJson(gcpPrivateKeyJson)
        .build();
  }

  /** A complete GCP_SERVICE_ACCOUNT request targeting a sovereign or mocked GCP. */
  public static SecretStoreRequest gcpServiceAccountRequestWithOverrides() {
    return gcpServiceAccountRequestWithProject().toBuilder()
        .gcpCustomResourceManagerUri(GCP_CUSTOM_RESOURCE_MANAGER_URI)
        .gcpCustomProjectsUri(GCP_CUSTOM_PROJECTS_URI)
        .build();
  }

  // -- GCP_OAUTH2 --

  /** A complete, valid GCP_OAUTH2 request (without project). */
  public static SecretStoreRequest gcpOAuth2Request() {
    return gcpOAuth2Request(
        GCP_SCOPE, null, GCP_OAUTH_CLIENT_ID, GCP_OAUTH_CLIENT_SECRET, GCP_OAUTH_REFRESH_TOKEN);
  }

  /** A complete, valid GCP_OAUTH2 request including a project. */
  public static SecretStoreRequest gcpOAuth2RequestWithProject() {
    return gcpOAuth2Request(
        GCP_SCOPE,
        GCP_PROJECT_ID,
        GCP_OAUTH_CLIENT_ID,
        GCP_OAUTH_CLIENT_SECRET,
        GCP_OAUTH_REFRESH_TOKEN);
  }

  public static SecretStoreRequest gcpOAuth2Request(
      String gcpScope,
      String gcpProjectId,
      String gcpOauthClientId,
      String gcpOauthClientSecret,
      String gcpOauthRefreshToken) {
    return SecretStoreRequest.builder()
        .gcpScope(gcpScope)
        .gcpProjectId(gcpProjectId)
        .gcpOauthClientId(gcpOauthClientId)
        .gcpOauthClientSecret(gcpOauthClientSecret)
        .gcpOauthRefreshToken(gcpOauthRefreshToken)
        .build();
  }

  /** A complete GCP_OAUTH2 request targeting a sovereign or mocked GCP. */
  public static SecretStoreRequest gcpOAuth2RequestWithOverrides() {
    return gcpOAuth2RequestWithProject().toBuilder()
        .gcpCustomTokenUri(GCP_CUSTOM_TOKEN_URI)
        .gcpCustomProjectsUri(GCP_CUSTOM_PROJECTS_URI)
        .gcpCustomIamUri(GCP_CUSTOM_IAM_URI)
        .gcpCustomOauthAccessToken(GCP_CUSTOM_OAUTH_ACCESS_TOKEN)
        .build();
  }
}
