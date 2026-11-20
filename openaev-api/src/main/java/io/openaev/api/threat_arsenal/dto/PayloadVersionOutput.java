package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** One version of a payload installed in the tenant. */
public record PayloadVersionOutput(
    @JsonProperty("payload_id") @NotBlank String id,
    @Schema(description = "The Threat Arsenal action of this version")
        @JsonProperty("payload_action_id")
        @NotBlank
        String actionId,
    @JsonProperty("payload_version") @NotBlank String version,
    @JsonProperty("payload_is_latest") @NotNull boolean latest,
    @Schema(description = "Its injects on older versions are kept there until a newer version")
        @JsonProperty("payload_older_versions_kept")
        @NotNull
        boolean olderVersionsKept,
    @JsonProperty("payload_revoked") @NotNull boolean revoked,
    @Schema(description = "Revoked by the catalogue: the tenant cannot lift it")
        @JsonProperty("payload_revoked_by_catalogue")
        @NotNull
        boolean revokedByCatalogue,
    @JsonProperty("payload_revocation_reason") String revocationReason,
    @JsonProperty("payload_revoked_at") Instant revokedAt,
    @Schema(description = "The admin who revoked it; empty when revoked by the catalogue")
        @JsonProperty("payload_revoked_by")
        String revokedBy) {}
