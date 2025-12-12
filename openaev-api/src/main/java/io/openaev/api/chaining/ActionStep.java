package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.Workflow;

public interface ActionStep {
  void create(StepsCreateInput.StepCreateInput step, Workflow workflow);

  void run(StepsCreateInput.StepCreateInput step, Workflow workflow);

  void end(StepsCreateInput.StepCreateInput step, Workflow workflow);
}
