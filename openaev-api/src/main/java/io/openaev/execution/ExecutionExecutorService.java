package io.openaev.execution;

import static io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_NAME;
import static io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_TYPE;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.executors.ExecutorContextService;
import io.openaev.rest.exception.AgentException;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.service.InjectService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class ExecutionExecutorService {

  private final ApplicationContext context;

  private final ExecutionTraceRepository executionTraceRepository;
  private final InjectService injectService;

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
    // Filter each list to do something for each specific case and then remove the specific agents
    // from the main "agents" list to execute payloads at the end for the remaining "normal" agents
    Set<Agent> inactiveAgents =
        agents.stream().filter(agent -> !agent.isActive()).collect(Collectors.toSet());
    agents.removeAll(inactiveAgents);
    Set<Agent> agentsWithoutExecutor =
        agents.stream().filter(agent -> agent.getExecutor() == null).collect(Collectors.toSet());
    agents.removeAll(agentsWithoutExecutor);
    Set<Agent> crowdstrikeAgents =
        agents.stream()
            .filter(agent -> CROWDSTRIKE_EXECUTOR_TYPE.equals(agent.getExecutor().getType()))
            .collect(Collectors.toSet());
    agents.removeAll(crowdstrikeAgents);

    AtomicBoolean atLeastOneExecution = new AtomicBoolean(false);
    // Manage inactive agents
    saveInactiveAgentsTraces(inactiveAgents, injectStatus);
    // Manage without executor agents
    saveWithoutExecutorAgentsTraces(agentsWithoutExecutor, injectStatus);
    // Manage Crowdstrike agents for batch execution
    if (!crowdstrikeAgents.isEmpty()) {
      try {
        ExecutorContextService executorContextService =
            context.getBean(CROWDSTRIKE_EXECUTOR_NAME, ExecutorContextService.class);
        executorContextService.launchBatchExecutorSubprocess(
            inject, crowdstrikeAgents, injectStatus);
        atLeastOneExecution.set(true);
      } catch (Exception e) {
        log.error("Crowdstrike launchBatchExecutorSubprocess error: {}", e.getMessage());
        saveCrowdstrikeAgentsErrorTraces(e, crowdstrikeAgents, injectStatus);
      }
    }
    // Manage remaining agents
    agents.forEach(
        agent -> {
          try {
            launchExecutorContextForAgent(inject, agent);
            atLeastOneExecution.set(true);
          } catch (AgentException e) {
            log.error("launchExecutorContextForAgent error: {}", e.getMessage());
            saveAgentErrorTrace(e, injectStatus);
          }
        });
    if (!atLeastOneExecution.get()) {
      throw new ExecutionExecutorException("No asset executed");
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
  public void saveCrowdstrikeAgentsErrorTraces(
      Exception e, Set<Agent> crowdstrikeAgents, InjectStatus injectStatus) {
    executionTraceRepository.saveAll(
        crowdstrikeAgents.stream()
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

  private void launchExecutorContextForAgent(Inject inject, Agent agent) throws AgentException {
    try {
      Endpoint assetEndpoint = (Endpoint) Hibernate.unproxy(agent.getAsset());
      ExecutorContextService executorContextService =
          context.getBean(agent.getExecutor().getName(), ExecutorContextService.class);
      executorContextService.launchExecutorSubprocess(inject, assetEndpoint, agent);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new AgentException("Fatal error: " + e.getMessage(), agent);
    }
  }
}
