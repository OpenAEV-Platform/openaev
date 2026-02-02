package io.openaev.service.chaining;

public interface StepEventHandler {
  void handleWaitStepEvent(StepEvent stepEvent);

  void handleDelayStepEvent(StepEvent stepEvent);
}
