package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.chaining.DataStep;
import io.openaev.database.model.STEP_ACTION_CLASS;
import java.util.List;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StepsCreateInput {
  public List<StepCreateInput> steps;

  @JsonProperty("workflow_id")
  private String workflowId;

  @Getter
  @Setter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class StepCreateInput {

    @JsonProperty("step_action")
    public STEP_ACTION_CLASS stepAction;

    @JsonProperty("limit_execution")
    public int limitExecution;

    @JsonProperty("conditions")
    public List<ConditionCreateInput> conditions;

    @JsonProperty("data_step")
    public DataStep dataStep;
  }
}
