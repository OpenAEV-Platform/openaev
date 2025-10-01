package io.openaev.rest.mapper.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class RuleAttributeUpdateInput {

  @JsonProperty("rule_attribute_id")
  private String id;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("rule_attribute_name")
  private String name;

  @JsonProperty("rule_attribute_columns")
  @Schema(nullable = true)
  private String columns;

  @JsonProperty("rule_attribute_default_value")
  private String defaultValue;

  @JsonProperty("rule_attribute_additional_config")
  private Map<String, String> additionalConfig = new HashMap<>();
}
