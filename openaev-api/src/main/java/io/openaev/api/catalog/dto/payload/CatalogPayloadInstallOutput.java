package io.openaev.api.catalog.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** The payloads an install added to the Threat Arsenal. */
public record CatalogPayloadInstallOutput(
    @JsonProperty("installed_payload_ids") List<String> payloadIds) {}
