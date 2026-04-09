package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.StepActionClass;
import io.openaev.rest.inject.form.InjectInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.*;

/** Input DTO for Step template CRUD operations. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StepInput {

  @JsonProperty("step_workflow_id")
  @NotBlank
  private String workflowId;

  @JsonProperty("step_action")
  @NotNull
  private StepActionClass stepAction;

  @JsonProperty("step_conditions")
  @Valid
  private List<ConditionCreateInput> conditions;

  /** IDs of existing condition trees (roots) to link to this step. */
  @JsonProperty("step_condition_ids")
  private List<String> conditionIds;

  @JsonProperty("step_data_step")
  private InjectInput dataStep;
}
