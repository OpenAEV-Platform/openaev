package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;

/** The interface Action step. IMPLEMENTED BY: - InjectExecutionStep */
public interface ActionStep {
  /**
   * Create step.
   *
   * @param stepInput the step input
   * @param workflow the workflow
   * @return the step
   */
  Step create(StepsCreateInput.StepCreateInput stepInput, Workflow workflow);

  /**
   * Creates a Wait step. The step is created with status WAIT based on a step template. Duplicates
   * the template step and fills its content from the input.
   *
   * @param stepTemplate the stepTemplate
   * @param input the input for the new step
   * @param workflowRun the workflow run
   * @return the created Wait step
   */
  Step wait(Step stepTemplate, String input, Workflow workflowRun);

  /**
   * Executes a Wait step. Changes the status from WAIT to RUN.
   *
   * @param waitStep the step currently in WAIT status
   * @return the step after being set to RUN
   */
  Step run(Step waitStep);

  /**
   * Updates a step. Applies the necessary processing based on the new output.
   *
   * @param stepRun the step run to update
   * @return the updated step
   */
  Step update(Step stepRun);

  /**
   * Ends a step. Checks if all expected outputs have been received and updates the status from RUN
   * to END.
   *
   * @param stepRun the step to end
   * @param workflow the workflow
   */
  void end(Step stepRun, Workflow workflow);
}
