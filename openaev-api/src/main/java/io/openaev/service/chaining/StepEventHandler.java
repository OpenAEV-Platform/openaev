package io.openaev.service.chaining;

/** Handler interface for processing step events in a workflow. */
public interface StepEventHandler {

  /**
   * Handles a step event when the step is ready to be executed.
   *
   * @param stepEvent the step event to handle
   */
  void handleReadyStepEvent(StepEvent stepEvent);

  /**
   * Handles a step event when the step execution should be delayed.
   *
   * @param stepEvent the step event to handle
   */
  void handleDelayStepEvent(StepEvent stepEvent);
}
