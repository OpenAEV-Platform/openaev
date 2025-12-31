package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;

public interface ActionStep {
  Step create(StepsCreateInput.StepCreateInput step, Workflow workflow);

  Step wait(Step step, String input, Workflow workflowRun);

  Step run(Step waitStep);

  Step update(Step stepRun);

  void end(StepsCreateInput.StepCreateInput step, Workflow workflow);
}
