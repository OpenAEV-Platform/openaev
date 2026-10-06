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
   * @return the step to set to RUN, or an empty Optional if the step failed for good: the caller
   *     then ends it without retry
   * @throws ChainingException if the execution fails: the transaction is rolled back and the event
   *     is retried
   */
  Optional<Step> run(Step readyStep) throws ChainingException;

  /**
   * Whether this READY step was already executed. A step runs once: a second READY request for it
   * (the same step published twice, e.g. a message still queued when resume republishes the step's
   * READY steps) is an anomaly the caller drops, without running the step again or touching it. No
   * default on purpose: each action states how it recognises its own past execution.
   *
   * @param readyStep the step a READY request asks to run, read under its row lock
   * @return {@code true} when the step already ran and must not run again
   */
  boolean isAlreadyRun(Step readyStep);

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
   */
  void end(Step stepRun) throws ChainingException;
}
