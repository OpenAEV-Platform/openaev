package io.openbas.execution;

import static io.openbas.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_NAME;
import static io.openbas.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_TYPE;

import io.openbas.database.model.*;
import io.openbas.database.repository.InjectStatusRepository;
import io.openbas.executors.ExecutorContextService;
import io.openbas.rest.exception.AgentException;
import io.openbas.rest.inject.service.InjectService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Log
public class ExecutionExecutorService {

  private final ApplicationContext context;

  private final InjectStatusRepository injectStatusRepository;
  private final InjectService injectService;

  public void launchExecutorContext(Inject inject) {
    // First, get the agents of this injects
    List<Agent> agents = this.injectService.getAgentsByInject(inject);
    // Filter each list to do something for each specific case and then remove the specific agents
    // from the main "agents" list to execute payloads at the end for the remaining "normal" agents
    List<Agent> inactiveAgents = agents.stream().filter(agent -> !agent.isActive()).toList();
    agents.removeAll(inactiveAgents);
    List<Agent> agentsWithoutExecutor =
        agents.stream().filter(agent -> agent.getExecutor() == null).toList();
    agents.removeAll(agentsWithoutExecutor);
    List<Agent> crowdstrikeAgents =
        agents.stream()
            .filter(agent -> CROWDSTRIKE_EXECUTOR_TYPE.equals(agent.getExecutor().getType()))
            .collect(Collectors.toList());
    agents.removeAll(crowdstrikeAgents);

    InjectStatus injectStatus =
        inject.getStatus().orElseThrow(() -> new IllegalArgumentException("Status should exist"));
    AtomicBoolean atLeastOneExecution = new AtomicBoolean(false);
    AtomicBoolean atLeastOneTraceAdded = new AtomicBoolean(false);
    // Manage inactive agents
    if (!inactiveAgents.isEmpty()) {
      inactiveAgents.forEach(
          agent ->
              injectStatus.addTrace(
                  ExecutionTraceStatus.AGENT_INACTIVE,
                  "Agent "
                      + agent.getExecutedByUser()
                      + " is inactive for the asset "
                      + agent.getAsset().getName(),
                  ExecutionTraceAction.COMPLETE,
                  agent));
      atLeastOneTraceAdded.set(true);
    }
    // Manage without executor agents
    if (!agentsWithoutExecutor.isEmpty()) {
      agentsWithoutExecutor.forEach(
          agent ->
              injectStatus.addTrace(
                  ExecutionTraceStatus.ERROR,
                  "Cannot find the executor for the agent "
                      + agent.getExecutedByUser()
                      + " from the asset "
                      + agent.getAsset().getName(),
                  ExecutionTraceAction.COMPLETE,
                  agent));
      atLeastOneTraceAdded.set(true);
    }
    // Manage Crowdstrike agents for batch execution
    try {
      ExecutorContextService executorContextService =
          context.getBean(CROWDSTRIKE_EXECUTOR_NAME, ExecutorContextService.class);
      crowdstrikeAgents =
          executorContextService.launchBatchExecutorSubprocess(
              inject, crowdstrikeAgents, injectStatus);
      atLeastOneExecution.set(true);
    } catch (Exception e) {
      log.severe("Crowdstrike launchBatchExecutorSubprocess error: " + e.getMessage());
      crowdstrikeAgents.forEach(
          agent ->
              injectStatus.addTrace(
                  ExecutionTraceStatus.ERROR,
                  e.getMessage(),
                  ExecutionTraceAction.COMPLETE,
                  agent));
      atLeastOneTraceAdded.set(true);
    }
    // Manage remaining agents
    agents.forEach(
        agent -> {
          try {
            launchExecutorContextForAgent(inject, agent);
            atLeastOneExecution.set(true);
          } catch (AgentException e) {
            log.severe("launchExecutorContextForAgent error: " + e.getMessage());
            injectStatus.addTrace(
                ExecutionTraceStatus.ERROR,
                e.getMessage(),
                ExecutionTraceAction.COMPLETE,
                e.getAgent());
            atLeastOneTraceAdded.set(true);
          }
        });
    // if launchExecutorContextForAgent fail for every agent we throw to manually set injectStatus
    // to error
    if (atLeastOneTraceAdded.get()) {
      this.injectStatusRepository.save(injectStatus);
    }
    if (!atLeastOneExecution.get()) {
      throw new ExecutionExecutorException("No asset executed");
    }
  }

  private void launchExecutorContextForAgent(Inject inject, Agent agent) throws AgentException {
    try {
      Endpoint assetEndpoint = (Endpoint) Hibernate.unproxy(agent.getAsset());
      ExecutorContextService executorContextService =
          context.getBean(agent.getExecutor().getName(), ExecutorContextService.class);
      executorContextService.launchExecutorSubprocess(inject, assetEndpoint, agent);
    } catch (Exception e) {
      log.severe(e.getMessage());
      throw new AgentException("Fatal error: " + e.getMessage(), agent);
    }
  }
}
