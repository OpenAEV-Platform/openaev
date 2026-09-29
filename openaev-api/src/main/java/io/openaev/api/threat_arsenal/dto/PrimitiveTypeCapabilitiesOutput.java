package io.openaev.api.threat_arsenal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * What a user can do with the values of a primitive chaining type.
 *
 * <p>The flags are {@link Boolean}, not {@code boolean}, so that Lombok generates {@code
 * getIsNumericValue()}: Jackson then derives the same name from the getter and from the field, and
 * serializes one property. A primitive would yield {@code isNumericValue()}, from which Jackson
 * infers {@code numericValue}, leaving field and getter as two distinct properties - and the
 * payload would carry both.
 */
@Getter
@Builder
@Schema(description = "Operator capabilities of a primitive chaining type.")
public class PrimitiveTypeCapabilitiesOutput {

  @Schema(
      description =
          "Values are numbers: the greater-than / less-than operators are meaningful and must be"
              + " offered, and a value must be numeric whatever the operator.")
  @JsonProperty("is_numeric_value")
  private Boolean isNumericValue;

  @Schema(
      description =
          "Comparing values depends on case: the case-sensitivity toggle is meaningful and must be"
              + " offered.")
  @JsonProperty("is_case_sensitivity")
  private Boolean isCaseSensitivity;
}
