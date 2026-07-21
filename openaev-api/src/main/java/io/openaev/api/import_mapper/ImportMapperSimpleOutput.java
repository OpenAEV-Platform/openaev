package io.openaev.api.import_mapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Listing projection: the importer graph is deliberately left out, pages never read it. */
public record ImportMapperSimpleOutput(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("import_mapper_id")
        String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("import_mapper_name")
        String name,
    @JsonProperty("import_mapper_created_at") Instant creationDate,
    @JsonProperty("import_mapper_updated_at") Instant updateDate) {}
