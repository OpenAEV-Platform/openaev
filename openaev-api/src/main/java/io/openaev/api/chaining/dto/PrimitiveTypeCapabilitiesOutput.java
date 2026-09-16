package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/** What a user can do with the values of a primitive chaining type. */
@Getter
@Builder
@Schema(description = "Operator capabilities of a primitive chaining type.")
public class PrimitiveTypeCapabilitiesOutput {

  @Schema(
      description =
          "Values are numbers: the greater-than / less-than operators are meaningful and must be"
              + " offered, and a value must be numeric whatever the operator.")
  @JsonProperty("numeric_value")
  private boolean numericValue;

  @Schema(
      description =
          "Comparing values depends on case: the case-sensitivity toggle is meaningful and must be"
              + " offered.")
  @JsonProperty("case_sensitivity")
  private boolean caseSensitivity;
}
