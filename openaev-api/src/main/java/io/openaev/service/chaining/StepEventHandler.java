package io.openaev.service.chaining;

public interface StepEventHandler {
  void handleReadyStepEvent(StepEvent stepEvent);

  void handleDelayStepEvent(StepEvent stepEvent);
}
