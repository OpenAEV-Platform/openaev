package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.catalog.CatalogHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** One version of a catalogue content installed or uninstalled in the tenant. */
public record CatalogHistoryOutput(
    @JsonProperty("catalog_history_id") @NotBlank String id,
    @JsonProperty("catalog_history_action") @NotNull CatalogHistory.Action action,
    @JsonProperty("catalog_history_mode") @NotNull CatalogHistory.Mode mode,
    @JsonProperty("catalog_history_version") @NotBlank String version,
    @Schema(description = "Who made the change; empty when the platform made it alone")
        @JsonProperty("catalog_history_user")
        String user,
    @JsonProperty("catalog_history_created_at") @NotNull Instant createdAt) {}
