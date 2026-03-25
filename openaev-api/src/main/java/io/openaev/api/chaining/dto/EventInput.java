package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/** Input DTO for creating or updating an event */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventInput {
  @JsonProperty("name")
  @NotBlank
  String name;

  @JsonProperty("description")
  String description;

  @JsonProperty("workflow_id")
  @NotBlank
  String workflowId;

  @JsonProperty("conditions")
  @NotEmpty
  @Valid
  List<ConditionCreateInput> conditions;

  /**
   * Optional step ID that this event depends on (step_from). Maps to the stepFrom field of the root
   * condition.
   */
  @JsonProperty("step_from")
  String stepFrom;

  /**
   * Optional list of step IDs to link to the root condition via the conditions_steps join table.
   * Each step will be linked with is_root=true on the root condition.
   */
  @JsonProperty("step_ids")
  List<String> stepIds = new ArrayList<>();
}
