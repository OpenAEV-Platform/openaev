package io.openaev.rest.payload.regex_group;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegexGroupInput {

  @JsonProperty("regex_group_id")
  private String id;

  @JsonProperty("regex_group_field")
  @Schema(description = "Field")
  @NotBlank
  private String field;

  @JsonProperty("regex_group_index_values")
  @Schema(description = "Index of the group from the regex match: $index0$index1")
  @NotBlank
  private String indexValues;
}
