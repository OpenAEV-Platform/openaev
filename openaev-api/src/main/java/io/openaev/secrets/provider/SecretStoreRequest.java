package io.openaev.secrets.provider;

import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AwsRegion;
import io.openaev.database.model.HashSecret;

/**
 * Payload for provider-side secret storage.
 *
 * @param username optional username for USERNAME_PASSWORD auth method
 * @param password plaintext password for USERNAME_PASSWORD auth method
 * @param hash plaintext hash for HASH auth method
 * @param hashAlgorithm hash algorithm for HASH auth method (NTLM, SHA256, ...)
 * @param awsDefaultRegion default AWS region for AWS auth methods
 * @param awsAccessKeyId optional access key id for ACCESS_KEY
 * @param awsSecretAccessKey optional secret access key for ACCESS_KEY
 * @param awsSessionToken optional session token for ACCESS_KEY
 * @param awsRoleArn optional role ARN for ASSUME_ROLE
 * @param awsExternalId optional external id for ASSUME_ROLE
 * @param awsSourceIdentityType optional source identity type for ASSUME_ROLE
 * @param awsSourceProfileAccessKeyId optional source profile access key id for ASSUME_ROLE
 * @param awsSourceProfileSecretAccessKey optional source profile secret access key for ASSUME_ROLE
 * @param azureEnvironment Azure cloud name for AZURE auth methods (see {@code AzureEnvironments})
 * @param azureClientId client id of the service principal, or of the user-assigned managed identity
 * @param azureClientSecret client secret for AZURE_SERVICE_PRINCIPAL
 * @param azureTenantId Entra ID tenant id for AZURE_SERVICE_PRINCIPAL
 * @param azureSubscriptionId optional subscription id for AZURE auth methods
 */
public record SecretStoreRequest(
    // IDENTITY CREDENTIALS
    String username,
    String password,
    String hash,
    HashSecret.HASH_ALGORITHM hashAlgorithm,
    // AWS CREDENTIALS
    AwsRegion awsDefaultRegion,
    String awsAccessKeyId,
    String awsSecretAccessKey,
    String awsSessionToken,
    String awsRoleArn,
    String awsExternalId,
    AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
    String awsSourceProfileAccessKeyId,
    String awsSourceProfileSecretAccessKey,
    // AZURE CREDENTIALS
    String azureEnvironment,
    String azureClientId,
    String azureClientSecret,
    String azureTenantId,
    String azureSubscriptionId) {}
