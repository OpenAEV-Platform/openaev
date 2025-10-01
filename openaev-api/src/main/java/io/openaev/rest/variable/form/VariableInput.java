package io.openaev.rest.variable.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VariableInput {

  @JsonProperty("variable_key")
  @NotBlank(message = MANDATORY_MESSAGE)
  @Pattern(regexp = "^[a-z_]+$")
  private String key;

  @JsonProperty("variable_value")
  private String value;

  @JsonProperty("variable_description")
  private String description;
}
