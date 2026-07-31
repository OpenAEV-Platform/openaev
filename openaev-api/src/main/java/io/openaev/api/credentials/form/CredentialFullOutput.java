package io.openaev.api.credentials.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.HashSecret;
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
    @Schema(description = "Credential status") @JsonProperty("credential_status") String status,
    @Schema(description = "Credential description") @JsonProperty("credential_description")
        String description,
    @Schema(description = "Secret username") @JsonProperty("credential_username") String username,
    @Schema(description = "Credential description") @JsonProperty("credential_hash_algorithm")
        HashSecret.HASH_ALGORITHM hashAlgorithm) {}
