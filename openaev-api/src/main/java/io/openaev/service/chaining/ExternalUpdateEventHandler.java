package io.openaev.service.chaining;

/** Handler interface for processing external update events in a workflow. */
public interface ExternalUpdateEventHandler {

  /**
   * Handles an external update event when an update in the workflow's run occurs.
   *
   * @param externalUpdateEvent the external update event to handle
   */
  void handleExternalUpdateEvent(ExternalUpdateEvent externalUpdateEvent);
}
