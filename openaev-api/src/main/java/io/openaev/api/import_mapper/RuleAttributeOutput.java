package io.openaev.api.import_mapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

public record RuleAttributeOutput(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("rule_attribute_id")
        String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("rule_attribute_name")
        String name,
    @JsonProperty("rule_attribute_columns") String columns,
    @JsonProperty("rule_attribute_default_value") String defaultValue,
    @JsonProperty("rule_attribute_additional_config") Map<String, String> additionalConfig,
    @JsonProperty("rule_attribute_created_at") Instant creationDate,
    @JsonProperty("rule_attribute_updated_at") Instant updateDate) {}
