package io.openaev.api.asset.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.AssetLinkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** A link to add from the asset of the path to another asset of the same tenant. */
public record AssetLinkInput(
    @JsonProperty("linked_asset_id") @NotBlank String linkedAssetId,
    @JsonProperty("asset_link_type") @NotNull AssetLinkType type) {}
