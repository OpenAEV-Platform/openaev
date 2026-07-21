package io.openaev.api.import_mapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record ImportMapperOutput(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("import_mapper_id")
        String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("import_mapper_name")
        String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("import_mapper_inject_type_column")
        String injectTypeColumn,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("import_mapper_inject_importers")
        List<InjectImporterOutput> injectImporters,
    @JsonProperty("import_mapper_created_at") Instant creationDate,
    @JsonProperty("import_mapper_updated_at") Instant updateDate) {}
