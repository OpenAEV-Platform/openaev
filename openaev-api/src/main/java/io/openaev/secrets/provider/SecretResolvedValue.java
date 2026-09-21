package io.openaev.secrets.provider;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AwsRegion;
import io.openaev.database.model.Secret;

/**
 * Typed resolved secret payload returned by a provider for runtime consumers such as external
 * injectors.
 *
 * <p>The top-level contract carries only the resolved secret kind and the matching value payload,
 * so callers never receive a flat object full of null fields and cannot infer unrelated supported
 * secret shapes from the response schema.
 */
@JsonInclude(NON_NULL)
public record SecretResolvedValue(
    @JsonProperty("type") Secret.SECRET_TYPE type, @JsonProperty("value") Value value) {

  public static SecretResolvedValue forUsernamePassword(String username, String password) {
    return new SecretResolvedValue(
        Secret.SECRET_TYPE.USERNAME_PASSWORD, new Value.UsernamePassword(username, password));
  }

  public static SecretResolvedValue forHash(
      io.openaev.database.model.HashSecret.HASH_ALGORITHM hashAlgorithm, String hash) {
    return new SecretResolvedValue(Secret.SECRET_TYPE.HASH, new Value.Hash(hashAlgorithm, hash));
  }

  public static SecretResolvedValue forAwsAccessKey(
      AwsRegion awsDefaultRegion,
      String awsAccessKeyId,
      String awsSecretAccessKey,
      String awsSessionToken) {
    return new SecretResolvedValue(
        Secret.SECRET_TYPE.AWS_ACCESS_KEY,
        new Value.AwsAccessKey(
            awsDefaultRegion, awsAccessKeyId, awsSecretAccessKey, awsSessionToken));
  }

  public static SecretResolvedValue forAwsAssumeRole(
      AwsRegion awsDefaultRegion,
      String awsRoleArn,
      AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
      String awsExternalId,
      String awsSourceProfileAccessKeyId,
      String awsSourceProfileSecretAccessKey) {
    return new SecretResolvedValue(
        Secret.SECRET_TYPE.AWS_ASSUME_ROLE,
        new Value.AwsAssumeRole(
            awsDefaultRegion,
            awsRoleArn,
            awsSourceIdentityType,
            awsExternalId,
            awsSourceProfileAccessKeyId,
            awsSourceProfileSecretAccessKey));
  }

  public static SecretResolvedValue forAzureServicePrincipal(
      String azureEnvironment,
      String azureClientId,
      String azureClientSecret,
      String azureTenantId,
      String azureSubscriptionId) {
    return new SecretResolvedValue(
        Secret.SECRET_TYPE.AZURE_SERVICE_PRINCIPAL,
        new Value.AzureServicePrincipal(
            azureEnvironment,
            azureClientId,
            azureClientSecret,
            azureTenantId,
            azureSubscriptionId));
  }

  public static SecretResolvedValue forAzureManagedIdentity(
      String azureEnvironment, String azureClientId, String azureSubscriptionId) {
    return new SecretResolvedValue(
        Secret.SECRET_TYPE.AZURE_MANAGED_IDENTITY,
        new Value.AzureManagedIdentity(azureEnvironment, azureClientId, azureSubscriptionId));
  }

  public static SecretResolvedValue forGcpServiceAccount(
      String gcpScope, String gcpProjectId, byte[] gcpPrivateKeyJson) {
    return new SecretResolvedValue(
        Secret.SECRET_TYPE.GCP_SERVICE_ACCOUNT,
        new Value.GcpServiceAccount(gcpScope, gcpProjectId, gcpPrivateKeyJson));
  }

  public static SecretResolvedValue forGcpOAuth2(
      String gcpScope,
      String gcpProjectId,
      String gcpOauthClientId,
      String gcpOauthClientSecret,
      String gcpOauthRefreshToken) {
    return new SecretResolvedValue(
        Secret.SECRET_TYPE.GCP_OAUTH2,
        new Value.GcpOAuth2(
            gcpScope, gcpProjectId, gcpOauthClientId, gcpOauthClientSecret, gcpOauthRefreshToken));
  }

  public sealed interface Value
      permits Value.UsernamePassword,
          Value.Hash,
          Value.AwsAccessKey,
          Value.AwsAssumeRole,
          Value.AzureServicePrincipal,
          Value.AzureManagedIdentity,
          Value.GcpServiceAccount,
          Value.GcpOAuth2 {

    @JsonInclude(NON_NULL)
    record UsernamePassword(
        @JsonProperty("username") String username, @JsonProperty("password") String password)
        implements Value {}

    @JsonInclude(NON_NULL)
    record Hash(
        @JsonProperty("hash_algorithm")
            io.openaev.database.model.HashSecret.HASH_ALGORITHM hashAlgorithm,
        @JsonProperty("hash") String hash)
        implements Value {}

    @JsonInclude(NON_NULL)
    record AwsAccessKey(
        @JsonProperty("aws_default_region") AwsRegion awsDefaultRegion,
        @JsonProperty("aws_access_key_id") String awsAccessKeyId,
        @JsonProperty("aws_secret_access_key") String awsSecretAccessKey,
        @JsonProperty("aws_session_token") String awsSessionToken)
        implements Value {}

    @JsonInclude(NON_NULL)
    record AwsAssumeRole(
        @JsonProperty("aws_default_region") AwsRegion awsDefaultRegion,
        @JsonProperty("aws_role_arn") String awsRoleArn,
        @JsonProperty("aws_source_identity_type")
            AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
        @JsonProperty("aws_external_id") String awsExternalId,
        @JsonProperty("aws_source_profile_access_key_id") String awsSourceProfileAccessKeyId,
        @JsonProperty("aws_source_profile_secret_access_key")
            String awsSourceProfileSecretAccessKey)
        implements Value {}

    @JsonInclude(NON_NULL)
    record AzureServicePrincipal(
        @JsonProperty("azure_environment") String azureEnvironment,
        @JsonProperty("azure_client_id") String azureClientId,
        @JsonProperty("azure_client_secret") String azureClientSecret,
        @JsonProperty("azure_tenant_id") String azureTenantId,
        @JsonProperty("azure_subscription_id") String azureSubscriptionId)
        implements Value {}

    @JsonInclude(NON_NULL)
    record AzureManagedIdentity(
        @JsonProperty("azure_environment") String azureEnvironment,
        @JsonProperty("azure_client_id") String azureClientId,
        @JsonProperty("azure_subscription_id") String azureSubscriptionId)
        implements Value {}

    @JsonInclude(NON_NULL)
    record GcpServiceAccount(
        @JsonProperty("gcp_scope") String gcpScope,
        @JsonProperty("gcp_project_id") String gcpProjectId,
        @JsonProperty("gcp_private_key_json") byte[] gcpPrivateKeyJson)
        implements Value {}

    @JsonInclude(NON_NULL)
    record GcpOAuth2(
        @JsonProperty("gcp_scope") String gcpScope,
        @JsonProperty("gcp_project_id") String gcpProjectId,
        @JsonProperty("gcp_oauth_client_id") String gcpOauthClientId,
        @JsonProperty("gcp_oauth_client_secret") String gcpOauthClientSecret,
        @JsonProperty("gcp_oauth_refresh_token") String gcpOauthRefreshToken)
        implements Value {}
  }
}
