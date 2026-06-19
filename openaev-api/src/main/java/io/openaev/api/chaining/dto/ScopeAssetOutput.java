package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An asset that is in scope (allowlisted and not denylisted) for a workflow.")
public record ScopeAssetOutput(
    @Schema(description = "ID of the asset") @JsonProperty("asset_id") String id,
    @Schema(description = "Name of the asset") @JsonProperty("asset_name") String name,
    @Schema(description = "Type of the asset (Endpoint, SecurityPlatform, …)")
        @JsonProperty("asset_type")
        String type,
    @Schema(description = "External reference of the asset")
        @JsonProperty("asset_external_reference")
        String externalReference) {}
