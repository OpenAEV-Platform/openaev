package io.openaev.api.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record PayloadRevocationOutput(
    @JsonProperty("content_revocation_id") @NotBlank String id,
    @JsonProperty("content_revocation_reason") String reason,
    @JsonProperty("content_revocation_revoked_at") Instant revokedAt,
    @JsonProperty("content_revocation_revoked_by") String revokedBy,
    @Schema(description = "tenant: revoked in this tenant; repository: from the revocation list")
        @JsonProperty("content_revocation_source")
        @NotBlank
        String source,
    @Schema(
            description =
                "Set when a customized payload inherits the revocation of this base version")
        @JsonProperty("content_revocation_inherited_from")
        String inheritedFrom) {

  public static final String SOURCE_TENANT = "tenant";
  public static final String SOURCE_REPOSITORY = "repository";
}
