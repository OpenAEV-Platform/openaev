package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.threat_arsenal.dto.PayloadVersionChangeOutput;
import io.openaev.database.model.catalog.ContentChange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** One change of a payload; an update or a save also says what its version changed. */
public record PayloadContentChangeOutput(
    @JsonProperty("content_change_id") @NotBlank String id,
    @JsonProperty("content_change_action") @NotNull ContentChange.Action action,
    @JsonProperty("content_change_mode") @NotNull ContentChange.Mode mode,
    @JsonProperty("content_change_version") @NotBlank String version,
    @Schema(description = "Who made the change; empty when the platform made it alone")
        @JsonProperty("content_change_user")
        String user,
    @JsonProperty("content_change_created_at") @NotNull Instant createdAt,
    @Schema(description = "The version it came from; empty when the change made no version")
        @JsonProperty("content_change_from_version")
        String fromVersion,
    @Schema(description = "The fields its version changed; empty when it made no version")
        @JsonProperty("content_change_fields")
        List<PayloadVersionChangeOutput> fields) {}
