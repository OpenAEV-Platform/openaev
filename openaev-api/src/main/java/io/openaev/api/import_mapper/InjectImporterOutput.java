package io.openaev.api.import_mapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record InjectImporterOutput(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("inject_importer_id")
        String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("inject_importer_type_value")
        String importTypeValue,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("inject_importer_injector_contract")
        String injectorContractId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("inject_importer_rule_attributes")
        List<RuleAttributeOutput> ruleAttributes,
    @JsonProperty("inject_importer_created_at") Instant creationDate,
    @JsonProperty("inject_importer_updated_at") Instant updateDate) {}
