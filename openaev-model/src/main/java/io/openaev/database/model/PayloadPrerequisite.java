package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.hash.Hashed;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.Data;

@Data
public class PayloadPrerequisite implements Serializable {

  @Hashed
  @NotBlank
  @JsonProperty("executor")
  private String executor;

  @Hashed
  @NotBlank
  @JsonProperty("get_command")
  private String getCommand;

  @Hashed
  @JsonProperty("check_command")
  private String checkCommand;

  @Hashed
  @JsonProperty("description")
  @Schema(types = {"string", "null"})
  private String description;
}
