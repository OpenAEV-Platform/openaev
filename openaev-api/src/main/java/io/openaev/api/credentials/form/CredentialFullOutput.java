package io.openaev.api.credentials.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import lombok.Builder;

@Builder
public record CredentialFullOutput(
    @Schema(description = "Credential ID") @NotNull @JsonProperty("credential_id") String id,
    @Schema(description = "Credential name") @NotNull @JsonProperty("credential_name") String name,
    @Schema(description = "Credential type") @NotNull @JsonProperty("credential_type")
        CredentialSecretReference.CREDENTIAL_TYPE credentialType,
    @Schema(description = "Credential authentication method")
        @NotNull
        @JsonProperty("credential_auth_method")
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD credentialAuthMethod,
    @Schema(description = "User who created the credential")
        @NotNull
        @JsonProperty("credential_created_by")
        CredentialOutput.CredentialCreatedByOutput createdBy,
    @Schema(description = "Tag IDs linked to the credential") @JsonProperty("credential_tags_ids")
        Set<String> tags,
    @Schema(description = "Last credential verification timestamp")
        @JsonProperty("credential_last_verified_at")
        Instant lastVerifiedAt,
    @Schema(description = "Credential creation timestamp")
        @NotNull
        @JsonProperty("credential_created_at")
        Instant createdAt,
    @Schema(description = "Credential status") @JsonProperty("credential_status")
        SecretReference.SECRET_STATUS status,
    @Schema(description = "Credential description") @JsonProperty("credential_description")
        String description,
    // IDENTITY
    @Schema(description = "Secret username") @JsonProperty("credential_username") String username,
    @Schema(description = "Secret hash algorithm") @JsonProperty("credential_hash_algorithm")
        HashSecret.HASH_ALGORITHM hashAlgorithm,
    // AWS
    @Schema(description = "Secret AWS default region")
        @JsonProperty("credential_aws_default_region")
        AwsRegion awsDefaultRegion,
    @Schema(description = "AWS access key ID") @JsonProperty("credential_aws_access_key_id")
        String awsAccessKeyId,
    @Schema(description = "AWS session token present")
        @JsonProperty("credential_aws_session_token_present")
        boolean awsSessionTokenPresent,
    @Schema(description = "AWS role ARN") @JsonProperty("credential_aws_role_arn")
        String awsRoleArn,
    @Schema(description = "AWS source identity type")
        @JsonProperty("credential_aws_source_identity_type")
        AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
    @Schema(description = "AWS source profile access key id")
        @JsonProperty("credential_aws_source_profile_access_key_id")
        String awsSourceProfileAccessKeyId,
    // AZURE
    @Schema(description = "Azure environment") @JsonProperty("credential_azure_environment")
        String azureEnvironment,
    @Schema(description = "Azure client id") @JsonProperty("credential_azure_client_id")
        String azureClientId,
    @Schema(description = "Azure tenant id") @JsonProperty("credential_azure_tenant_id")
        String azureTenantId,
    @Schema(description = "Azure subscription id") @JsonProperty("credential_azure_subscription_id")
        String azureSubscriptionId,
    // GCP
    @Schema(description = "GCP OAuth scope") @JsonProperty("credential_gcp_scope") String gcpScope,
    @Schema(description = "GCP project id") @JsonProperty("credential_gcp_project_id")
        String gcpProjectId,
    @Schema(
            description =
                "Whether a GCP service account key file is stored; the key itself is never returned")
        @JsonProperty("credential_gcp_private_key_defined")
        boolean gcpPrivateKeyDefined) {}
