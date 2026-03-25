package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConditionType;
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
  @JsonProperty("temporary_id")
  private String temporaryId;

  /** Temporary ID of the parent condition */
  @Schema(description = "Temporary ID of the parent condition")
  @JsonProperty("temporary_id_condition_parent")
  private String temporaryIdConditionParent;

  /** Condition key: Path to the value in the output of the step from */
  @Schema(description = "Path to the value in the output of the step from")
  @JsonProperty("key_type")
  private String keyType;

  /** Condition value: Value to be compared */
  @Schema(description = "Value to be compared")
  @JsonProperty("value")
  private String value;

  /**
   * "Condition type: AND, OR, EQ, NEQ, IS_NULL, IS_NOT_NULL, GT, GTE, LT, LTE, IN, NIN, AFTER,
   * BEFORE, MAPPER, or DEPEND_ON"
   */
  @Schema(
      description =
          "Condition type: AND, OR, EQ, NEQ, IS_NULL, IS_NOT_NULL, GT, GTE, LT, LTE, IN, NIN, AFTER, BEFORE, MAPPER, or DEPEND_ON")
  @JsonProperty("type")
  private ConditionType type;

  /** ID of the step linked to the key */
  @Schema(description = "ID of the step linked to the key")
  @JsonProperty("step_from")
  private String stepFrom;
}
