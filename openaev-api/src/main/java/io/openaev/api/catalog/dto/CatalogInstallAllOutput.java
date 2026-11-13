package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** What an install of every new payload or scenario added, and the ones it could not install. */
public record CatalogInstallAllOutput(
    @JsonProperty("installed_ids") List<String> installedIds,
    @JsonProperty("failed_external_ids") List<String> failedExternalIds) {}
