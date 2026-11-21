package io.openaev.api.asset.software.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One software component to record on an asset. A missing or blank version is stored as an unknown
 * version. Recording a component the asset already has (same PURL, else CPE, else vendor, product
 * and version) updates it and moves its last seen date.
 */
public record AssetSoftwareInput(
    @JsonProperty("asset_software_vendor") @Size(max = 512) String vendor,
    @JsonProperty("asset_software_product") @NotBlank @Size(max = 512) String product,
    @JsonProperty("asset_software_version") @Size(max = 255) String version,
    @JsonProperty("asset_software_cpe") @Size(max = 1024) String cpe,
    @JsonProperty("asset_software_purl") @Size(max = 2048) String purl,
    @JsonProperty("asset_software_parent_id") String parentId) {}
