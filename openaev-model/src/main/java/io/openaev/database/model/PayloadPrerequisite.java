package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayloadPrerequisite {

  @NotBlank
  @JsonProperty("executor")
  private String executor;

  @NotBlank
  @JsonProperty("get_command")
  private String getCommand;

  @JsonProperty("check_command")
  private String checkCommand;

  @JsonProperty("description")
  @Schema(nullable = true)
  private String description;
}
