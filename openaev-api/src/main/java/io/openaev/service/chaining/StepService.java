package io.openaev.service.chaining;

import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.STEP_ACTION_CLASS;
import io.openaev.database.model.Workflow;
import io.openaev.rest.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StepService {
    WorkflowService workflowService;
    public void saveStep(){

    }

    public void createSteps(String workflowId, List<StepsCreateInput.StepCreateInput> steps) {
        Workflow workflow = workflowService.getWorkflowById(workflowId);
        for (StepsCreateInput.StepCreateInput step : steps) {
            ActionStep actionStep = this.factoryAction(step.getStepAction());
            if(actionStep == null) throw new BadRequestException("action step is null");
            actionStep.create(step, workflow);
        }

    }
    private ActionStep factoryAction(STEP_ACTION_CLASS actionClass){
        return switch (actionClass){
            case STEP_ACTION_CLASS.INJECT_EXECUTION -> new InjectExecutionStep();
            default -> null;
        };
    }

}
