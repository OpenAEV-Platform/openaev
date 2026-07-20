package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.MappingType;
import io.openaev.database.model.PrimitiveType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/** The DTO for creation of a condition to execute a step. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(
    description =
        "Condition used to execute a step. Can be a Template or an Execution depending on the status of stepFrom.")
public class ConditionCreateInput {

  /** Temporary ID of the condition */
  @Schema(description = "Temporary ID of the condition")
  @JsonProperty("condition_temporary_id")
  private String temporaryId;

  /** Temporary ID of the parent condition */
  @Schema(description = "Temporary ID of the parent condition")
  @JsonProperty("condition_temporary_id_condition_parent")
  private String temporaryIdConditionParent;

  /** Condition key Type: Path to the value in the output of the step from */
  @Schema(description = "Path to the value in the output of the step from")
  @JsonProperty("condition_key_type")
  private PrimitiveType keyType;

  /** Condition value: Value to be compared */
  @Schema(description = "Value to be compared")
  @JsonProperty("condition_value")
  private String value;

  /** Whether the comparison is case-sensitive (default: true) */
  @Schema(description = "Whether the comparison is case-sensitive")
  @JsonProperty("condition_case_sensitive")
  @Builder.Default
  private boolean caseSensitive = true;

  /** Condition key: Property to be mapped */
  @Schema(description = "Property to be mapped")
  @JsonProperty("condition_key")
  private String key;

  /**
   * "Condition type: AND, OR, EQ, NEQ, IS_NULL, IS_NOT_NULL, GT, GTE, LT, LTE, IN, NIN, AFTER,
   * BEFORE, MAPPER, or DEPEND_ON"
   */
  @Schema(
      description =
          "Condition type: AND, OR, EQ, NEQ, IS_NULL, IS_NOT_NULL, GT, GTE, LT, LTE, IN, NIN, AFTER, BEFORE, MAPPER, or DEPEND_ON")
  @JsonProperty("condition_type")
  private ConditionType type;

  /**
   * Mapping type: DEFAULT, LOCAL, or GLOBAL. Required when condition type is MAPPER, must be null
   * otherwise.
   */
  @Schema(
      description =
          "Mapping type: DEFAULT, LOCAL, or GLOBAL. Required when condition type is MAPPER, must be null otherwise.")
  @JsonProperty("condition_mapping_type")
  private MappingType mappingType;

  /** ID of the step linked to the key - time-based logic */
  @Schema(description = "ID of the step linked to the key")
  @JsonProperty("condition_step_from")
  private String stepFrom;
}
