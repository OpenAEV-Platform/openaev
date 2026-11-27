package io.openaev.api.catalog.dto.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/** An offered version of a catalogue payload. */
public record CatalogPayloadOutput(
    @JsonProperty("catalog_payload_external_id") @NotBlank String externalId,
    @JsonProperty("catalog_payload_version") @NotBlank String version,
    @JsonProperty("catalog_payload_name") String name,
    @JsonProperty("catalog_payload_published_at") Instant publishedAt) {}
