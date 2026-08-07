package io.openaev.execution;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.utils.ExecutorUtils;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.exception.AgentException;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.EndpointService;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
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
  private final InjectStatusRepository injectStatusRepository;
  private final InjectService injectService;
  private final ExecutorUtils executorUtils;
  private final EndpointService endpointService;
  private final ConnectorInstanceService connectorInstanceService;
  private final ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  private final ActionMetricCollector actionMetricCollector;
  private final Optional<AuditLogger> auditLogger;

  public void launchExecutorContext(Inject inject) {
    InjectStatus injectStatus =
        inject.getStatus().orElseThrow(() -> new IllegalArgumentException("Status should exist"));
    // First, get the agents and the assets agentless of this inject
    AgentsAndAssetsAgentless agentsAndAssetsAgentless =
        this.injectService.getAgentsAndAgentlessAssetsByInject(inject);
    Set<Agent> agents = agentsAndAssetsAgentless.agents();
    Set<Asset> assetsAgentless = agentsAndAssetsAgentless.assetsAgentless();
    // Persist the number of agents resolved at launch so the COMPLETE callback path can decide
    // completion without re-resolving the full asset/agent graph on every callback
    injectStatus.setExpectedAgentCount(agents.size());
    injectStatusRepository.save(injectStatus);
    // Manage agentless assets
    saveAgentlessAssetsTraces(assetsAgentless, injectStatus);
    // Filter inactive and executor-less agents
    Set<Agent> inactiveAgents = executorUtils.findInactiveAgents(agents);
    agents.removeAll(inactiveAgents);
    // When an executor becomes inactive, remove its source tag from endpoints
    endpointService.removeSourceTagsFromAgentEndpoints(inactiveAgents);
    Set<Agent> agentsWithoutExecutor = executorUtils.findAgentsWithoutExecutor(agents);
    agents.removeAll(agentsWithoutExecutor);
    Set<Agent> overloadedAgents = executorUtils.findOverloadedAgents(agents);
    agents.removeAll(overloadedAgents);

    AtomicBoolean atLeastOneExecution = new AtomicBoolean(false);
    // Manage inactive agents
    saveInactiveAgentsTraces(inactiveAgents, injectStatus);
    // Manage without executor agents
    saveWithoutExecutorAgentsTraces(agentsWithoutExecutor, injectStatus);
    // Manage overloaded agents
    saveOverloadedAgentsTraces(overloadedAgents, injectStatus);

    // Group remaining agents by their executor entity for per-instance routing.
    // Each executor entity maps to exactly one ConnectorInstance (and therefore one Integration
    // with its own API client/config)
    Map<io.openaev.database.model.Executor, Set<Agent>> agentsByExecutor =
        agents.stream().collect(Collectors.groupingBy(Agent::getExecutor, Collectors.toSet()));

    // Inject-level (global) trace so the "Execution details" tab is not empty for payload/executor
    // injects: the detailed command output is recorded per agent, but the global timeline needs a
    // summary showing the platform distributing the payload to the resolved agents.
    saveDistributionTrace(agents, injectStatus);

    for (Map.Entry<io.openaev.database.model.Executor, Set<Agent>> entry :
        agentsByExecutor.entrySet()) {
      io.openaev.database.model.Executor executor = entry.getKey();
      Set<Agent> executorAgents = entry.getValue();
      logInjectExecutingEvent(inject, executor, executorAgents);
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
        Manager manager = managerFactory.getManager(inject.getTenant().getId());
        ExecutorContextService executorContextService;
        if (executor.isExternal()) {
          // Resolve the ConnectorInstance that owns this executor, scoped to the inject's tenant
          ConnectorInstancePersisted instance =
              connectorInstanceService.findByExecutorId(
                  executor.getId(), inject.getTenant().getId());
          executorContextService =
              manager.requestForInstance(instance, ExecutorContextService.class);
        } else {
          // Fallback for builtin executors without a persisted ConnectorInstance (e.g. OpenAEV
          // agent)
          executorContextService =
              manager.request(
                  new ComponentRequest(executor.getName()), ExecutorContextService.class);
        }
        String token =
            serviceAccountPrivilegeService.getTokenUserServiceAccountByTenant(
                inject.getTenant().getId());
        List<Agent> agentsProcessed =
            executorContextService.launchBatchExecutorSubprocess(
                inject, agents, injectStatus, token);
        List<Agent> remainingAgents = new ArrayList<>(agents);
        remainingAgents.removeAll(agentsProcessed);
        // Also handle individual execution for executor context services whose batch
        // implementation is a no-op (e.g. OpenAEV agent)
        for (Agent agent : remainingAgents) {
          Endpoint assetEndpoint = (Endpoint) Hibernate.unproxy(agent.getAsset());
          executorContextService.launchExecutorSubprocess(inject, assetEndpoint, agent, token);
        }
        atLeastOneExecution.set(true);
        actionMetricCollector.addExecutorUsedCount(executor.getType());
      } catch (Exception e) {
        log.error(
            "{} (id={}) launchBatchExecutorSubprocess error: {}",
            executor.getName(),
            executor.getId(),
            e.getMessage(),
            e);
        saveAgentsErrorTraces(e, agents, injectStatus);
      }
    }
  }

  /**
   * Writes a single inject-level (global) INFO trace summarising the distribution of a
   * payload/executor inject to its resolved agents. Global traces carry no agent and no context
   * identifiers, so they surface in the "Execution details" tab (which previously showed nothing
   * for executor injects, whose per-agent traces are only visible on each endpoint).
   */
  @VisibleForTesting
  public void saveDistributionTrace(Set<Agent> agents, InjectStatus injectStatus) {
    if (agents.isEmpty()) {
      return;
    }
    long endpointCount =
        agents.stream()
            .map(Agent::getAsset)
            .filter(Objects::nonNull)
            .map(Asset::getId)
            .distinct()
            .count();
    String message =
        "Distributing inject to "
            + agents.size()
            + " agent(s) across "
            + endpointCount
            + " endpoint(s)";
    executionTraceRepository.save(
        new ExecutionTrace(
            injectStatus,
            ExecutionTraceStatus.INFO,
            List.of(),
            message,
            ExecutionTraceAction.START,
            null,
            null));
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
  public void saveOverloadedAgentsTraces(Set<Agent> overloadedAgents, InjectStatus injectStatus) {
    if (!overloadedAgents.isEmpty()) {
      executionTraceRepository.saveAll(
          overloadedAgents.stream()
              .map(
                  agent ->
                      new ExecutionTrace(
                          injectStatus,
                          ExecutionTraceStatus.AGENT_OVERLOADED,
                          List.of(),
                          "Agent "
                              + agent.getExecutedByUser()
                              + " is overloaded for the asset "
                              + agent.getAsset().getName()
                              + " (queue threshold exceeded)",
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

  // -- AUDIT LOGGING --

  private void logInjectExecutingEvent(
      Inject inject, Executor executor, Set<Agent> executorAgents) {
    if (executorAgents.isEmpty()) {
      return;
    }
    auditLogger.ifPresent(
        logger ->
            logger.logEvent(
                AuditEvent.builder()
                    .eventType(EventType.EXECUTION)
                    .eventScope(AuditEventScope.INJECT_QUEUED)
                    .eventStatus(EventStatus.SUCCESS)
                    .resourceType(ResourceType.INJECT)
                    .resourceId(inject.getId())
                    .message(
                        "Inject '%s' executing on '%s' agent(s)"
                            .formatted(inject.getTitle(), executor.getName()))
                    .contextData(buildInjectQueuedContextData(inject, executor, executorAgents))
                    .origin(AuditEventOrigin.SYSTEM)
                    .build()));
  }

  private Map<String, Object> buildInjectQueuedContextData(
      Inject inject, Executor executor, Set<Agent> executorAgents) {
    Map<String, Object> contextData = new LinkedHashMap<>();
    contextData.put("inject_id", inject.getId());
    contextData.put("inject_name", inject.getTitle());
    contextData.put("executor_id", executor.getId());
    contextData.put("executor_type", executor.getType());
    contextData.put(
        "agent_ids", executorAgents.stream().map(Agent::getId).filter(Objects::nonNull).toList());
    if (inject.getExercise() != null) {
      contextData.put("simulation_id", inject.getExercise().getId());
    }
    return contextData;
  }
}
