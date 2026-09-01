package io.openaev.secrets.provider;

import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AwsRegion;
import io.openaev.database.model.HashSecret;

/**
 * Safe metadata returned by secret providers for credential display/update helpers.
 *
 * <p>It intentionally excludes any secret value (password/hash/client secret/service account key),
 * even encrypted, and more generally any field flagged as sensitive: this record is serialized to
 * the API through {@code CredentialFullOutput}. Sensitive fields do not need to be echoed back, as
 * the form renders them as write-only placeholders.
 */
public record SecretMetadata(
    String username,
    HashSecret.HASH_ALGORITHM hashAlgorithm,
    AwsRegion awsDefaultRegion,
    String awsAccessKeyId,
    boolean awsSessionTokenPresent,
    String awsRoleArn,
    AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
    String awsSourceProfileAccessKeyId,
    String azureEnvironment,
    String azureClientId,
    String azureTenantId,
    String azureSubscriptionId,
    String gcpScope,
    String gcpProjectId,
    boolean gcpPrivateKeyDefined,
    String gcpOauthClientId,
    boolean gcpOauthClientSecretDefined,
    boolean gcpOauthRefreshTokenDefined) {

  public static SecretMetadata empty() {
    return new SecretMetadata(
        null, null, null, null, false, null, null, null, null, null, null, null, null, null, false,
        null, false, false);
  }

  public static SecretMetadata forUsername(String username) {
    return new SecretMetadata(
        username, null, null, null, false, null, null, null, null, null, null, null, null, null,
        false, null, false, false);
  }

  public static SecretMetadata forHashAlgorithm(HashSecret.HASH_ALGORITHM hashAlgorithm) {
    return new SecretMetadata(
        null,
        hashAlgorithm,
        null,
        null,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        false,
        false);
  }

  public static SecretMetadata forAwsAccessKey(
      AwsRegion awsDefaultRegion, String awsAccessKeyId, boolean awsSessionTokenPresent) {
    return new SecretMetadata(
        null,
        null,
        awsDefaultRegion,
        awsAccessKeyId,
        awsSessionTokenPresent,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        false,
        false);
  }

  public static SecretMetadata forAwsAssumeRole(
      AwsRegion awsDefaultRegion,
      String awsRoleArn,
      AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
      String awsSourceProfileAccessKeyId) {
    return new SecretMetadata(
        null,
        null,
        awsDefaultRegion,
        null,
        false,
        awsRoleArn,
        awsSourceIdentityType,
        awsSourceProfileAccessKeyId,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        false,
        false);
  }

  /**
   * Non-sensitive metadata of an Azure service principal secret.
   *
   * <p>Only the client secret is left out: the tenant id and the subscription id are directory
   * identifiers, not credentials, so they are stored in clear text and echoed back to prefill the
   * edit form.
   *
   * @param azureEnvironment Azure cloud name
   * @param azureClientId client id of the service principal
   * @param azureTenantId Entra ID tenant id owning the service principal
   * @param azureSubscriptionId targeted subscription id, may be null
   * @return matching metadata
   */
  public static SecretMetadata forAzureServicePrincipal(
      String azureEnvironment,
      String azureClientId,
      String azureTenantId,
      String azureSubscriptionId) {
    return new SecretMetadata(
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        azureEnvironment,
        azureClientId,
        azureTenantId,
        azureSubscriptionId,
        null,
        null,
        false,
        null,
        false,
        false);
  }

  /**
   * Non-sensitive metadata of an Azure managed identity secret.
   *
   * <p>The client id is only set for user-assigned identities. The subscription id is not a
   * credential and is echoed back to prefill the edit form.
   *
   * @param azureEnvironment Azure cloud name
   * @param azureClientId client id of the user-assigned managed identity, null when system-assigned
   * @param azureSubscriptionId targeted subscription id, may be null
   * @return matching metadata
   */
  public static SecretMetadata forAzureManagedIdentity(
      String azureEnvironment, String azureClientId, String azureSubscriptionId) {
    return new SecretMetadata(
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        azureEnvironment,
        azureClientId,
        null,
        azureSubscriptionId,
        null,
        null,
        false,
        null,
        false,
        false);
  }

  /**
   * Non-sensitive metadata of a GCP service account secret.
   *
   * <p>The key file itself IS the credential and never leaves the backend, not even encrypted: only
   * a boolean tells the form that one is stored, so it can render its write-only placeholder and
   * treat an absent upload as "keep the stored key".
   *
   * @param gcpScope the OAuth scope the credential is stored for
   * @param gcpProjectId targeted project id, may be null
   * @param gcpPrivateKeyDefined whether a service account key file is currently stored
   * @return matching metadata
   */
  public static SecretMetadata forGcpServiceAccount(
      String gcpScope, String gcpProjectId, boolean gcpPrivateKeyDefined) {
    return new SecretMetadata(
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        gcpScope,
        gcpProjectId,
        gcpPrivateKeyDefined,
        null,
        false,
        false);
  }

  /**
   * Non-sensitive metadata of a GCP OAuth 2.0 secret.
   *
   * <p>The client id is a public application identifier, echoed back to prefill the edit form —
   * this mirrors {@link #forAzureServicePrincipal}, which returns the client id and deliberately
   * leaves the client secret out. The OAuth client secret and the refresh token ARE the credential
   * and never leave the backend, not even encrypted: a refresh token is a long-lived bearer
   * credential, and leaking it is equivalent to leaking the account. Only booleans tell the form
   * that both are stored, so it can render their write-only placeholders and treat an absent value
   * as "keep the stored one".
   *
   * @param gcpScope the OAuth scope the credential is stored for
   * @param gcpProjectId targeted project id, may be null
   * @param gcpOauthClientId OAuth client id of the application
   * @param gcpOauthClientSecretDefined whether an OAuth client secret is currently stored
   * @param gcpOauthRefreshTokenDefined whether an OAuth refresh token is currently stored
   * @return matching metadata
   */
  public static SecretMetadata forGcpOAuth2(
      String gcpScope,
      String gcpProjectId,
      String gcpOauthClientId,
      boolean gcpOauthClientSecretDefined,
      boolean gcpOauthRefreshTokenDefined) {
    return new SecretMetadata(
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        gcpScope,
        gcpProjectId,
        false,
        gcpOauthClientId,
        gcpOauthClientSecretDefined,
        gcpOauthRefreshTokenDefined);
  }
}
