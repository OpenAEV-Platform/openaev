package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ArgumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Input DTO for creating or updating a workflow scope variable.
 *
 * <p>When {@code id} is {@code null}, a new variable is created. When {@code id} is provided, the
 * existing variable with that ID is updated in-place.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Input for a scope variable attached to a workflow.")
public class ScopeVariableInput {

  @Schema(
      description = "ID of an existing scope variable. Null means a new variable will be created.")
  @JsonProperty("scope_variable_id")
  private String id;

  @NotBlank
  @Schema(
      description = "Unique key used to reference the variable in templates (e.g. company_name).")
  @JsonProperty("scope_variable_key")
  private String key;

  @NotNull
  @Schema(description = "Argument type driving how the variable value is interpreted.")
  @JsonProperty("scope_variable_type")
  private ArgumentType type;

  @NotBlank
  @Schema(description = "Value of the variable.")
  @JsonProperty("scope_variable_value")
  private String value;

  @Schema(description = "Optional description of the variable's purpose.")
  @JsonProperty("scope_variable_description")
  private String description;
}
