package io.openaev.api.asset.software.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** What an SBOM import stored on the asset: counts, then every component it read. */
public record SbomImportOutput(
    @JsonProperty("created") int created,
    @JsonProperty("updated") int updated,
    @JsonProperty("unknown_versions") int unknownVersions,
    @JsonProperty("software") List<AssetSoftwareOutput> software) {}
