package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.api.chaining.InputStep;
import io.openaev.database.model.STEP_ACTION_CLASS;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class StepsCreateInput {
    public List<StepCreateInput> steps;

    @JsonProperty("workflow_id")
    private String workflowId;

    @Getter
    @Setter
    @Builder
    public static class StepCreateInput {

        @JsonProperty("step_action")
        public STEP_ACTION_CLASS stepAction;

        @JsonProperty("limit_execution")
        public int limitExecution;

        @JsonProperty("conditions")
        public List<ConditionCreateInput> conditions;

        @JsonProperty("input_step")
        public InputStep inputStep;
    }
}
