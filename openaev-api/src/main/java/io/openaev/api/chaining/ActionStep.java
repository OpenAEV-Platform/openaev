package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.rest.exception.ChainingException;
import java.util.Optional;

/** The interface Action step. IMPLEMENTED BY: - InjectExecutionStep */
public interface ActionStep {
  /**
   * Create step.
   *
   * @param stepInput the step input
   * @param workflow the workflow
   * @return the step
   */
  Optional<Step> create(StepsCreateInput.StepInput stepInput, Workflow workflow)
      throws ChainingException;

  /**
   * Creates a Ready step. The step is created with status READY based on a step template.
   * Duplicates the template step and fills its content from the input.
   *
   * @param stepTemplate the stepTemplate
   * @param input the input for the new step
   * @param workflowRun the workflow run
   * @return the created Ready step
   */
  Optional<Step> ready(Step stepTemplate, String input, Workflow workflowRun)
      throws ChainingException;

  /**
   * Executes a Ready step. Changes the status from READY to RUN.
   *
   * @param readyStep the step currently in READY status
   * @return the step after being set to RUN
   */
  Optional<Step> run(Step readyStep) throws ChainingException;

  /**
   * Updates a step. Applies the necessary processing based on the new output.
   *
   * @param stepRun the step run to update
   * @return the updated step
   */
  Optional<Step> update(Step stepRun) throws ChainingException;

  /**
   * Ends a step. Checks if all expected outputs have been received and updates the status from RUN
   * to END.
   *
   * @param stepRun the step to end
   * @param workflow the workflow
   */
  void end(Step stepRun, Workflow workflow) throws ChainingException;
}
