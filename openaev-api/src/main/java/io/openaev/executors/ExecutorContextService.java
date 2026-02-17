package io.openaev.executors;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.rest.exception.AgentException;
import java.util.List;
import java.util.Set;

public abstract class ExecutorContextService {

  /**
   * Launch one attack for one agent, deprecation in progress, only used by Caldera/OpenAEV agent
   *
   * @param inject to launch for the attack
   * @param assetEndpoint concerned by the attack
   * @param agent concerned by the attack
   * @throws AgentException if problem
   */
  public abstract void launchExecutorSubprocess(Inject inject, Endpoint assetEndpoint, Agent agent)
      throws AgentException;

  /**
   * To use when possible for better performance, launch one attack for X agents, used by
   * Tanium/SentinelOne/Crowdstrike
   *
   * @param inject to launch for the attack
   * @param agents concerned by the attack
   * @param injectStatus concerned by the attack
   * @return agents
   * @throws InterruptedException if problem
   */
  public abstract List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus);
}
