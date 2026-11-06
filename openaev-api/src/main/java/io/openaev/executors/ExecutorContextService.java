package io.openaev.executors;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.rest.exception.AgentException;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ExecutorContextService {

  private static final Logger DISPATCH_LOG = LoggerFactory.getLogger(ExecutorContextService.class);

  /**
   * Launch one attack for one agent, deprecation in progress, only used by Caldera/OpenAEV agent
   *
   * @param inject to launch for the attack
   * @param assetEndpoint concerned by the attack
   * @param agent concerned by the attack
   * @throws AgentException if problem
   */
  public abstract void launchExecutorSubprocess(
      Inject inject, Endpoint assetEndpoint, Agent agent, String token) throws AgentException;

  /**
   * To use when possible for better performance, launch one attack for X agents, used by
   * Tanium/SentinelOne/Crowdstrike/PaloAltoCortex
   *
   * @param inject to launch for the attack
   * @param agents concerned by the attack
   * @param injectStatus concerned by the attack
   * @return agents
   * @throws InterruptedException if problem
   */
  public abstract List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus, String token);

  /**
   * Release the resources this context service owns, called when its executor integration stops. A
   * new context service is built on every start, so a resource kept here would outlive the
   * integration. Already scheduled dispatches still run; nothing new is accepted.
   */
  public void shutdown() {}

  /**
   * Dispatch every action of a page on its own, so a failure on one agent never aborts the dispatch
   * of the agents after it. Runs on the dispatch worker, where an uncaught exception would unwind
   * the whole page and be lost with the discarded future.
   *
   * @param actions the page of actions to dispatch
   * @param dispatch the vendor call for one action
   * @param agentReference the agent external reference of an action, for the log
   * @param executorName the executor name, for the log
   */
  protected static <T> void dispatchEach(
      List<T> actions,
      Consumer<T> dispatch,
      Function<T, String> agentReference,
      String executorName) {
    for (T action : actions) {
      try {
        dispatch.accept(action);
      } catch (RuntimeException e) {
        DISPATCH_LOG.error(
            "{} dispatch failed for agent {}, continuing with the rest of the page: {}",
            executorName,
            agentReference.apply(action),
            e.getMessage(),
            e);
      }
    }
  }
}
