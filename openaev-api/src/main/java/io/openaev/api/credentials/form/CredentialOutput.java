package io.openaev.api.credentials.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.CredentialSecretReference;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.Builder;

@Builder
public record CredentialOutput(
    @Schema(description = "Credential ID") @JsonProperty("credential_id") String id,
    @Schema(description = "Credential name") @JsonProperty("credential_name") String name,
    @Schema(description = "Credential type") @JsonProperty("credential_type")
        CredentialSecretReference.CREDENTIAL_TYPE credentialType,
    @Schema(description = "Credential authentication method")
        @JsonProperty("credential_auth_method")
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD credentialAuthMethod,
    @Schema(description = "Credential status") @JsonProperty("credential_status") String status,
    @Schema(description = "Credential creation timestamp") @JsonProperty("credential_created_at")
        Instant createdAt,
    @Schema(description = "User who created the credential") @JsonProperty("credential_created_by")
        CredentialCreatedByOutput createdBy,
    @Schema(description = "Tag IDs linked to the credential") @JsonProperty("credential_tags_ids")
        Set<String> tags,
    @Schema(description = "Last credential verification timestamp")
        @JsonProperty("credential_last_verified_at")
        Instant lastVerifiedAt) {

  public record CredentialCreatedByOutput(
      @Schema(description = "Creator user ID") @JsonProperty("user_id") String userId,
      @Schema(description = "Creator display name") @JsonProperty("user_name") String userName) {}
}
