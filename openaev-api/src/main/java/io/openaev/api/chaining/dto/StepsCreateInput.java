package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.chaining.DataInputStep;
import io.openaev.database.model.StepActionClass;
import java.util.List;
import lombok.*;

/** The DTO for creation of steps */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StepsCreateInput {
  /** The Steps. */
  @JsonProperty("steps")
  public List<StepInput> steps;

  /** Workflow template ID */
  @JsonProperty("workflow_id")
  private String workflowId;

  /** The DTO for creation of step */
  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class StepInput {

    /**
     * Step action: INJECT_EXECUTION. A step can process different actions depending on its step
     * action. Each action implements ActionStep and its methods.
     */
    @JsonProperty("step_action")
    private StepActionClass stepAction;

    /**
     * Execution limit. Applies to a step template and limits the number of executions for the same
     * step template, regardless of incoming inputs.
     */
    @JsonProperty("limit_execution")
    private int limitExecution;

    /**
     * Conditions. List of conditions evaluated to determine whether a step execution should be
     * processed.
     */
    @JsonProperty("conditions")
    private List<ConditionCreateInput> conditions;

    /** IDs of existing condition trees (roots) to link to this step. */
    @JsonProperty("condition_ids")
    private List<String> conditionIds;

    /**
     * Data Step. Contains the expected object depending on the step action. INJECT_EXECUTION →
     * InjectInput
     */
    @JsonProperty("data_step")
    private DataInputStep dataStep;
  }
}
