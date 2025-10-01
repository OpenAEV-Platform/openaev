package io.openaev.executors;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.rest.exception.AgentException;
import java.util.List;
import java.util.Set;

public abstract class ExecutorContextService {

  public abstract void launchExecutorSubprocess(Inject inject, Endpoint assetEndpoint, Agent agent)
      throws AgentException;

  public abstract List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) throws InterruptedException;
}
