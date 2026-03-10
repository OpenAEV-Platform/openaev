package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayloadArgument {
  @NotBlank
  @JsonProperty("type")
  private String type;

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

  @JsonProperty("input_sources")
  @Schema(
      description =
          "Compatible upstream output fields. When set, this argument can be auto-populated"
              + " from any matching output type sub-field of an upstream action.")
  private List<InputSource> inputSources;
}
