package io.openaev.rest.payload.regex_group;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Represents the groups defined by the regex pattern.")
public class RegexGroupSimple {

  @JsonProperty("regex_group_id")
  @NotBlank
  private String id;

  @JsonProperty("regex_group_field")
  @Schema(description = "Represents the field name of specific captured groups.")
  @NotBlank
  private String field;

  @JsonProperty("regex_group_index_values")
  @Schema(description = "Represents the indexes of specific captured groups.")
  @NotBlank
  private String indexValues;
}
