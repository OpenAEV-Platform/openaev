package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ArgumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/** Output DTO for a workflow scope variable. */
@Getter
@Builder
@Schema(description = "Output for a scope variable attached to a workflow.")
public class ScopeVariableOutput {

  @Schema(description = "Unique ID of the scope variable.")
  @JsonProperty("scope_variable_id")
  private String id;

  @Schema(description = "Key used to reference the variable in templates.")
  @JsonProperty("scope_variable_key")
  private String key;

  @Schema(description = "Argument type driving how the variable value is interpreted.")
  @JsonProperty("scope_variable_type")
  private ArgumentType type;

  @Schema(description = "Value of the variable.")
  @JsonProperty("scope_variable_value")
  private String value;

  @Schema(description = "Optional description of the variable's purpose.")
  @JsonProperty("scope_variable_description")
  private String description;
}
