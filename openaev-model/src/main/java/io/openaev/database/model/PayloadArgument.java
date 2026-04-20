package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PayloadArgument {

  @NotNull
  @JsonProperty("type")
  private ArgumentType type;

  @JsonProperty("subtype")
  @Schema(nullable = true, description = "Optional sub-field key for structured output types")
  private ArgumentSubType subtype;

  @NotBlank
  @JsonProperty("key")
  private String key;

  @NotBlank
  @JsonProperty("default_value")
  private String defaultValue;

  @JsonProperty("description")
  @Schema(types = {"string", "null"})
  private String description;

  @JsonProperty("separator")
  @Schema(types = {"string", "null"})
  private String separator;
}
