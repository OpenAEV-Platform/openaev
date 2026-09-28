package io.openaev.api.credentials.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AwsRegion;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.HashSecret;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CredentialInput(
    @JsonProperty("credential_name") @NotBlank String credentialName,
    @JsonProperty("credential_type") @NotNull
        CredentialSecretReference.CREDENTIAL_TYPE credentialType,
    @JsonProperty("credential_auth_method") @NotNull
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD credentialAuthMethod,
    @JsonProperty("credential_description") String credentialDescription,
    @JsonProperty("credential_username") String credentialUsername,
    @JsonProperty("credential_password") String credentialPassword,
    @JsonProperty("credential_hash_algorithm") HashSecret.HASH_ALGORITHM credentialHashAlgorithm,
    @JsonProperty("credential_hash") String credentialHash,
    @JsonProperty("aws_default_region") AwsRegion awsDefaultRegion,
    @JsonProperty("aws_access_key_id") String awsAccessKeyId,
    @JsonProperty("aws_secret_access_key") String awsSecretAccessKey,
    @JsonProperty("aws_session_token") String awsSessionToken,
    @JsonProperty("aws_role_arn") String awsRoleArn,
    @JsonProperty("aws_external_id") String awsExternalId,
    @JsonProperty("aws_source_identity_type")
        AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
    @JsonProperty("aws_source_profile_access_key_id") String awsSourceProfileAccessKeyId,
    @JsonProperty("aws_source_profile_secret_access_key") String awsSourceProfileSecretAccessKey,
    @JsonProperty("azure_environment") String azureEnvironment,
    @JsonProperty("azure_client_id") String azureClientId,
    @JsonProperty("azure_client_secret") String azureClientSecret,
    @JsonProperty("azure_tenant_id") String azureTenantId,
    @JsonProperty("azure_subscription_id") String azureSubscriptionId,
    @JsonProperty("gcp_scope") String gcpScope,
    @JsonProperty("gcp_project_id") String gcpProjectId,
    @JsonProperty("gcp_oauth_client_id") String gcpOauthClientId,
    @JsonProperty("gcp_oauth_client_secret") String gcpOauthClientSecret,
    @JsonProperty("gcp_oauth_refresh_token") String gcpOauthRefreshToken,
    @JsonProperty("credential_tags") List<String> credentialTagIds) {

  public CredentialInput(
      String credentialName,
      CredentialSecretReference.CREDENTIAL_TYPE credentialType,
      CredentialSecretReference.CREDENTIAL_AUTH_METHOD credentialAuthMethod,
      String credentialDescription,
      String credentialUsername,
      String credentialPassword,
      HashSecret.HASH_ALGORITHM credentialHashAlgorithm,
      String credentialHash,
      List<String> credentialTagIds) {
    this(
        credentialName,
        credentialType,
        credentialAuthMethod,
        credentialDescription,
        credentialUsername,
        credentialPassword,
        credentialHashAlgorithm,
        credentialHash,
        // AWS
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        // AZURE
        null,
        null,
        null,
        null,
        null,
        // GCP
        null,
        null,
        null,
        null,
        null,
        credentialTagIds);
  }
}
