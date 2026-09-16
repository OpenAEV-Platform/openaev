package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** How the value of a primitive chaining type is validated. */
@Getter
@Builder
@Schema(description = "Value format validation of a primitive chaining type.")
public class PrimitiveTypeValidationOutput {

  @Schema(
      description =
          "Operators the rules apply to. Deliberately excludes IS_NULL / IS_NOT_NULL, which carry"
              + " no value, and IN / NIN, which are evaluated as substring matches so a partial"
              + " value is legitimate.")
  @JsonProperty("applies_to")
  private List<ConditionType> appliesTo;

  @Schema(
      description =
          "Alternative rules, combined with OR semantics. Empty when the type constrains no format,"
              + " in which case any value is accepted.")
  @JsonProperty("rules")
  private List<PrimitiveTypeFormatRuleOutput> rules;
}
