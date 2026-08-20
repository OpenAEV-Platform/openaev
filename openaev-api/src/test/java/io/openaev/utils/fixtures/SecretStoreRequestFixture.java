package io.openaev.utils.fixtures;

import io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE;
import io.openaev.database.model.AwsRegion;
import io.openaev.secrets.provider.SecretStoreRequest;

/** Fixtures for {@link SecretStoreRequest} payloads used by the secret handlers. */
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

  public static final String AZURE_ENVIRONMENT = "AzureCloud";
  public static final String AZURE_OTHER_ENVIRONMENT = "AzureUSGovernment";
  public static final String AZURE_CLIENT_ID = "11111111-1111-1111-1111-111111111111";
  public static final String AZURE_OTHER_CLIENT_ID = "22222222-2222-2222-2222-222222222222";
  public static final String AZURE_CLIENT_SECRET = "azureClientSecret-EXAMPLE";
  public static final String AZURE_TENANT_ID = "33333333-3333-3333-3333-333333333333";
  public static final String AZURE_SUBSCRIPTION_ID = "44444444-4444-4444-4444-444444444444";

  private SecretStoreRequestFixture() {}

  /** A request carrying no value at all — every optional field is {@code null}. */
  public static SecretStoreRequest emptyRequest() {
    return new SecretStoreRequest(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null);
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
    return new SecretStoreRequest(
        null,
        null,
        null,
        null,
        awsDefaultRegion,
        awsAccessKeyId,
        awsSecretAccessKey,
        awsSessionToken,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
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
    return new SecretStoreRequest(
        null,
        null,
        null,
        null,
        awsDefaultRegion,
        null,
        null,
        null,
        awsRoleArn,
        awsExternalId,
        awsSourceIdentityType,
        awsSourceProfileAccessKeyId,
        awsSourceProfileSecretAccessKey,
        null,
        null,
        null,
        null,
        null);
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
    return new SecretStoreRequest(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        azureEnvironment,
        azureClientId,
        azureClientSecret,
        azureTenantId,
        azureSubscriptionId);
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
    return new SecretStoreRequest(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        azureEnvironment,
        azureClientId,
        null,
        null,
        azureSubscriptionId);
  }
}
