package io.openaev.api.catalog.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** The payloads an install of every new payload added, and those it could not install. */
public record CatalogPayloadInstallAllOutput(
    @JsonProperty("installed_payload_ids") List<String> installedPayloadIds,
    @JsonProperty("failed_payload_external_ids") List<String> failedPayloadExternalIds) {}
