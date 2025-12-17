package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;

public interface ActionStep {
  Step create(StepsCreateInput.StepCreateInput step, Workflow workflow);

  Step wait(Step step, String input);

  Step run(Step waitStep, Workflow workflow);

  void update(StepsCreateInput.StepCreateInput step, Workflow workflow);

  void end(StepsCreateInput.StepCreateInput step, Workflow workflow);
}
