package io.openaev.secrets.provider;

import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AwsRegion;
import io.openaev.database.model.HashSecret;
import lombok.Builder;

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
 * @param gcpScope mandatory OAuth scope for GCP auth methods
 * @param gcpProjectId optional project id for GCP auth methods
 * @param gcpPrivateKeyJson raw content of the uploaded service account key file, for
 *     GCP_SERVICE_ACCOUNT; null means "left untouched by the client"
 * @param gcpOauthClientId OAuth client id for GCP_OAUTH2
 * @param gcpOauthClientSecret OAuth client secret for GCP_OAUTH2
 * @param gcpOauthRefreshToken OAuth refresh token for GCP_OAUTH2
 * @param awsAccountId optional AWS account id, for both AWS auth methods
 * @param awsEndpointUrl optional endpoint override of a sovereign or mocked AWS, for both AWS auth
 *     methods
 * @param gcpCustomTokenUri optional token endpoint override for GCP_OAUTH2
 * @param gcpCustomProjectsUri optional projects endpoint override, for both GCP auth methods
 * @param gcpCustomResourceManagerUri optional resource manager endpoint override for
 *     GCP_SERVICE_ACCOUNT
 * @param gcpCustomIamUri optional IAM endpoint override for GCP_OAUTH2
 * @param gcpCustomOauthAccessToken optional OAuth access token for GCP_OAUTH2
 * @param azureAuthorityHostUri optional authority host override, for both Azure auth methods
 * @param azureRequestsCertificate raw content of the uploaded certificate file, for both Azure auth
 *     methods; null means "left untouched by the client"
 */
@Builder(toBuilder = true)
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
    String azureSubscriptionId,
    // GCP CREDENTIALS
    String gcpScope,
    String gcpProjectId,
    byte[] gcpPrivateKeyJson,
    String gcpOauthClientId,
    String gcpOauthClientSecret,
    String gcpOauthRefreshToken,
    // SOVEREIGN OR MOCKED CLOUD OVERRIDES
    String awsAccountId,
    String awsEndpointUrl,
    String gcpCustomTokenUri,
    String gcpCustomProjectsUri,
    String gcpCustomResourceManagerUri,
    String gcpCustomIamUri,
    String gcpCustomOauthAccessToken,
    String azureAuthorityHostUri,
    byte[] azureRequestsCertificate) {}
