package io.openaev.execution;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.utils.ExecutorUtils;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.exception.AgentException;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class ExecutionExecutorService {

  private final ManagerFactory managerFactory;
  private final ExecutionTraceRepository executionTraceRepository;
  private final InjectService injectService;
  private final ExecutorUtils executorUtils;
  private final ConnectorInstanceService connectorInstanceService;

  public void launchExecutorContext(Inject inject) {
    InjectStatus injectStatus =
        inject.getStatus().orElseThrow(() -> new IllegalArgumentException("Status should exist"));
    // First, get the agents and the assets agentless of this inject
    AgentsAndAssetsAgentless agentsAndAssetsAgentless =
        this.injectService.getAgentsAndAgentlessAssetsByInject(inject);
    Set<Agent> agents = agentsAndAssetsAgentless.agents();
    Set<Asset> assetsAgentless = agentsAndAssetsAgentless.assetsAgentless();
    // Manage agentless assets
    saveAgentlessAssetsTraces(assetsAgentless, injectStatus);
    // Filter inactive and executor-less agents
    Set<Agent> inactiveAgents = executorUtils.findInactiveAgents(agents);
    agents.removeAll(inactiveAgents);
    Set<Agent> agentsWithoutExecutor = executorUtils.findAgentsWithoutExecutor(agents);
    agents.removeAll(agentsWithoutExecutor);

    AtomicBoolean atLeastOneExecution = new AtomicBoolean(false);
    // Manage inactive agents
    saveInactiveAgentsTraces(inactiveAgents, injectStatus);
    // Manage without executor agents
    saveWithoutExecutorAgentsTraces(agentsWithoutExecutor, injectStatus);

    // Group remaining agents by their executor entity for per-instance routing.
    // Each executor entity maps to exactly one ConnectorInstance (and therefore one Integration
    // with its own API client/config)
    Map<io.openaev.database.model.Executor, Set<Agent>> agentsByExecutor =
        agents.stream().collect(Collectors.groupingBy(Agent::getExecutor, Collectors.toSet()));

    for (Map.Entry<io.openaev.database.model.Executor, Set<Agent>> entry :
        agentsByExecutor.entrySet()) {
      io.openaev.database.model.Executor executor = entry.getKey();
      Set<Agent> executorAgents = entry.getValue();
      launchBatchExecutorContextForAgent(
          executorAgents, executor, inject, injectStatus, atLeastOneExecution);
    }

    if (!atLeastOneExecution.get()) {
      throw new ExecutionExecutorException("No asset executed");
    }
  }

  private void launchBatchExecutorContextForAgent(
      Set<Agent> agents,
      Executor executor,
      Inject inject,
      InjectStatus injectStatus,
      AtomicBoolean atLeastOneExecution) {
    if (!agents.isEmpty()) {
      try {
        Manager manager = managerFactory.getManager();
        ExecutorContextService executorContextService;
        if (executor.isExternal()) {
          // Resolve the ConnectorInstance that owns this executor
          ConnectorInstancePersisted instance =
              connectorInstanceService.findByExecutorId(executor.getId());
          executorContextService =
              manager.requestForInstance(instance, ExecutorContextService.class);
        } else {
          // Fallback for builtin executors without a persisted ConnectorInstance (e.g. OpenAEV
          // agent)
          executorContextService =
              manager.request(
                  new ComponentRequest(executor.getName()), ExecutorContextService.class);
        }
        List<Agent> agentsProcessed =
            executorContextService.launchBatchExecutorSubprocess(inject, agents, injectStatus);
        List<Agent> remainingAgents = new ArrayList<>(agents);
        remainingAgents.removeAll(agentsProcessed);
        // Also handle individual execution for executor context services whose batch
        // implementation is a no-op (e.g. OpenAEV agent)
        for (Agent agent : remainingAgents) {
          Endpoint assetEndpoint = (Endpoint) Hibernate.unproxy(agent.getAsset());
          executorContextService.launchExecutorSubprocess(inject, assetEndpoint, agent);
        }
        atLeastOneExecution.set(true);
      } catch (Exception e) {
        log.error(
            "{} (id={}) launchBatchExecutorSubprocess error: {}",
            executor.getName(),
            executor.getId(),
            e.getMessage());
        saveAgentsErrorTraces(e, agents, injectStatus);
      }
    }
  }

  @VisibleForTesting
  public void saveAgentErrorTrace(AgentException e, InjectStatus injectStatus) {
    executionTraceRepository.save(
        new ExecutionTrace(
            injectStatus,
            ExecutionTraceStatus.ERROR,
            List.of(),
            e.getMessage(),
            ExecutionTraceAction.COMPLETE,
            e.getAgent(),
            null));
  }

  @VisibleForTesting
  public void saveAgentsErrorTraces(Exception e, Set<Agent> agents, InjectStatus injectStatus) {
    executionTraceRepository.saveAll(
        agents.stream()
            .map(
                agent ->
                    new ExecutionTrace(
                        injectStatus,
                        ExecutionTraceStatus.ERROR,
                        List.of(),
                        e.getMessage(),
                        ExecutionTraceAction.COMPLETE,
                        agent,
                        null))
            .toList());
  }

  @VisibleForTesting
  public void saveWithoutExecutorAgentsTraces(
      Set<Agent> agentsWithoutExecutor, InjectStatus injectStatus) {
    if (!agentsWithoutExecutor.isEmpty()) {
      executionTraceRepository.saveAll(
          agentsWithoutExecutor.stream()
              .map(
                  agent ->
                      new ExecutionTrace(
                          injectStatus,
                          ExecutionTraceStatus.ERROR,
                          List.of(),
                          "Cannot find the executor for the agent "
                              + agent.getExecutedByUser()
                              + " from the asset "
                              + agent.getAsset().getName(),
                          ExecutionTraceAction.COMPLETE,
                          agent,
                          null))
              .toList());
    }
  }

  @VisibleForTesting
  public void saveInactiveAgentsTraces(Set<Agent> inactiveAgents, InjectStatus injectStatus) {
    if (!inactiveAgents.isEmpty()) {
      executionTraceRepository.saveAll(
          inactiveAgents.stream()
              .map(
                  agent ->
                      new ExecutionTrace(
                          injectStatus,
                          ExecutionTraceStatus.AGENT_INACTIVE,
                          List.of(),
                          "Agent "
                              + agent.getExecutedByUser()
                              + " is inactive for the asset "
                              + agent.getAsset().getName(),
                          ExecutionTraceAction.COMPLETE,
                          agent,
                          null))
              .toList());
    }
  }

  @VisibleForTesting
  public void saveAgentlessAssetsTraces(Set<Asset> assetsAgentless, InjectStatus injectStatus) {
    if (!assetsAgentless.isEmpty()) {
      executionTraceRepository.saveAll(
          assetsAgentless.stream()
              .map(
                  asset ->
                      new ExecutionTrace(
                          injectStatus,
                          ExecutionTraceStatus.ASSET_AGENTLESS,
                          List.of(asset.getId()),
                          "Asset " + asset.getName() + " has no agent, unable to launch the inject",
                          ExecutionTraceAction.COMPLETE,
                          null,
                          null))
              .toList());
    }
  }
}
