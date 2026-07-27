package io.openaev.api.asset;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Filter option for an asset, enriched with its product-facing category so pickers can group the
 * inventory (Host, Web application, AI target...). The {@code id}/{@code label} pair matches the
 * generic option contract used by every other filter option endpoint.
 */
public record AssetOptionOutput(
    @Schema(description = "Asset id") @JsonProperty("id") String id,
    @Schema(description = "Asset name") @JsonProperty("label") String label,
    @Schema(description = "Product-facing asset category, used to group options in pickers")
        @JsonProperty("category")
        String category) {}
