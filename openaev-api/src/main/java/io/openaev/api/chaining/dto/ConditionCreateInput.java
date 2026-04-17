package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConditionKeySubtype;
import io.openaev.database.model.ConditionKeyType;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.MappingType;
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

  /** Condition key: Path to the value in the output of the step from */
  @Schema(description = "Path to the value in the output of the step from")
  @JsonProperty("condition_key_type")
  private ConditionKeyType keyType;

  /** Condition key subtype */
  @Schema(description = "Condition key subtype")
  @JsonProperty("condition_key_subtype")
  private ConditionKeySubtype keySubtype;

  /** Condition value: Value to be compared */
  @Schema(description = "Value to be compared")
  @JsonProperty("condition_value")
  private String value;

  /** Condition key: Key to be compared */
  @Schema(description = "Key to be compared")
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
