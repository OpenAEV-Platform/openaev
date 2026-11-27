package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.hash.FileReference;
import io.openaev.database.model.hash.Hashed;
import io.openaev.database.model.hash.NotHashed;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.Data;

@Data
public class PayloadArgument implements Serializable {

  @Hashed
  @NotNull
  @JsonProperty("type")
  private PrimitiveType type;

  @Hashed
  @NotBlank
  @JsonProperty("key")
  private String key;

  @NotHashed
  @NotBlank
  @JsonProperty("default_value")
  private String defaultValue;

  @Hashed
  @JsonProperty("description")
  @Schema(types = {"string", "null"})
  private String description;

  @Hashed
  @JsonProperty("separator")
  @Schema(types = {"string", "null"})
  private String separator;

  @Hashed(key = "default_value")
  public Object hashedDefaultValue() {
    return type == PrimitiveType.Document ? new FileReference(defaultValue) : defaultValue;
  }
}
