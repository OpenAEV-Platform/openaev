package io.openaev.executors.model;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Inject;

/** The inject and agent an executor sends a payload for, kept to report back on the inject. */
public record ExecutorDispatch(String injectId, String tenantId, String agentId) {

  public static ExecutorDispatch of(Inject inject, Agent agent) {
    return new ExecutorDispatch(inject.getId(), inject.getTenant().getId(), agent.getId());
  }
}
