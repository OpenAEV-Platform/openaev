package io.openaev.api.asset.software.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.SoftwareSource;
import io.openaev.database.model.SoftwareVersionState;
import java.time.Instant;

/**
 * One software component of an asset, with its asset named so a search across assets reads without
 * a second call. {@code asset_software_version} is null exactly when {@code
 * asset_software_version_state} is {@code UNKNOWN}.
 */
public record AssetSoftwareOutput(
    @JsonProperty("asset_software_id") String id,
    @JsonProperty("asset_id") String assetId,
    @JsonProperty("asset_name") String assetName,
    @JsonProperty("asset_software_parent_id") String parentId,
    @JsonProperty("asset_software_vendor") String vendor,
    @JsonProperty("asset_software_product") String product,
    @JsonProperty("asset_software_version") String version,
    @JsonProperty("asset_software_version_state") SoftwareVersionState versionState,
    @JsonProperty("asset_software_cpe") String cpe,
    @JsonProperty("asset_software_purl") String purl,
    @JsonProperty("asset_software_source") SoftwareSource source,
    @JsonProperty("asset_software_first_seen") Instant firstSeen,
    @JsonProperty("asset_software_last_seen") Instant lastSeen) {}
