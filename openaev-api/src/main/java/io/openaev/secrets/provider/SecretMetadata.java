package io.openaev.secrets.provider;

import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AwsRegion;
import io.openaev.database.model.HashSecret;

/**
 * Safe metadata returned by secret providers for credential display/update helpers.
 *
 * <p>It intentionally excludes any secret value (password/hash/client secret), even encrypted, and
 * more generally any field flagged as sensitive: this record is serialized to the API through
 * {@code CredentialFullOutput}. Sensitive fields do not need to be echoed back, as the form renders
 * them as write-only placeholders.
 */
public record SecretMetadata(
    String username,
    HashSecret.HASH_ALGORITHM hashAlgorithm,
    AwsRegion awsDefaultRegion,
    String awsAccessKeyId,
    String awsRoleArn,
    AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
    String awsSourceProfileAccessKeyId,
    String azureEnvironment,
    String azureClientId,
    String azureTenantId,
    String azureSubscriptionId) {

  public static SecretMetadata empty() {
    return new SecretMetadata(null, null, null, null, null, null, null, null, null, null, null);
  }

  public static SecretMetadata forUsername(String username) {
    return new SecretMetadata(username, null, null, null, null, null, null, null, null, null, null);
  }

  public static SecretMetadata forHashAlgorithm(HashSecret.HASH_ALGORITHM hashAlgorithm) {
    return new SecretMetadata(
        null, hashAlgorithm, null, null, null, null, null, null, null, null, null);
  }

  public static SecretMetadata forAwsAccessKey(AwsRegion awsDefaultRegion, String awsAccessKeyId) {
    return new SecretMetadata(
        null, null, awsDefaultRegion, awsAccessKeyId, null, null, null, null, null, null, null);
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
        awsRoleArn,
        awsSourceIdentityType,
        awsSourceProfileAccessKeyId,
        null,
        null,
        null,
        null);
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
        null,
        null,
        null,
        azureEnvironment,
        azureClientId,
        azureTenantId,
        azureSubscriptionId);
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
        null,
        null,
        null,
        azureEnvironment,
        azureClientId,
        null,
        azureSubscriptionId);
  }
}
