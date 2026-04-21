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
  @JsonProperty("event_name")
  @NotBlank
  String name;

  @JsonProperty("event_description")
  String description;

  @JsonProperty("event_workflow_id")
  @NotBlank
  String workflowId;

  @JsonProperty("event_conditions")
  @NotEmpty
  @Valid
  List<ConditionCreateInput> conditions;

  /**
   * Optional list of step IDs to link to the root condition via the conditions_steps join table.
   * Each step will be linked with is_root=true on the root condition.
   */
  @JsonProperty("event_step_ids")
  List<String> stepIds = new ArrayList<>();
}
