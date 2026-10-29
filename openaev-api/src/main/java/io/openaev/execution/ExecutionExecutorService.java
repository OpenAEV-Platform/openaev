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
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.ResolvedAssetsToExecute;
import io.openaev.service.EndpointService;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class ExecutionExecutorService {

  private static final String ASSET_NOT_TARGETED_MESSAGE = "Asset not targeted";

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

  public void launchExecutorContext(Inject inject, ResolvedAssetsToExecute resolvedAssets) {
    InjectStatus injectStatus =
        inject.getStatus().orElseThrow(() -> new IllegalArgumentException("Status should exist"));
    // First, get the agents and the assets agentless of this inject restricted by the launch by
    // clearance, from the dispatch's own target resolution
    AgentsAndAssetsAgentless agentsAndAssetsAgentless =
        this.injectService.toAgentsAndAgentlessAssets(resolvedAssets);
    Set<Agent> agents = agentsAndAssetsAgentless.agents();
    Set<Agent> agentsWithoutClearance = agentsAndAssetsAgentless.agentsWithoutClearance();
    Set<Asset> assetsAgentless = agentsAndAssetsAgentless.assetsAgentless();
    // Persist the number of agents resolved at launch so the COMPLETE callback path can decide
    // completion without re-resolving the full asset/agent graph on every callback
    injectStatus.setExpectedAgentCount(agents.size() + agentsWithoutClearance.size());
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
    // Manage assets not cleared
    saveAgentsWithoutClearanceTraces(agentsWithoutClearance, injectStatus);
    saveAssetsWithoutClearanceTraces(
        agentsAndAssetsAgentless.assetsWithoutClearance(), injectStatus);

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
    ExecutionTrace trace =
        new ExecutionTrace(
            injectStatus,
            ExecutionTraceStatus.INFO,
            List.of(),
            message,
            ExecutionTraceAction.START,
            null,
            null);
    injectStatus.addTrace(trace);
    executionTraceRepository.save(trace);
  }

  /**
   * Writes one terminal ERROR trace per agent, carrying the exception message, so a failure while
   * dispatching the inject to an executor is reported on each affected agent instead of being lost.
   */
  @VisibleForTesting
  public void saveAgentsErrorTraces(Exception e, Set<Agent> agents, InjectStatus injectStatus) {
    saveAgentTraces(agents, injectStatus, ExecutionTraceStatus.ERROR, agent -> e.getMessage());
  }

  /**
   * Writes one terminal ERROR trace per agent that has no executor attached, since the inject
   * cannot be dispatched to them and they would otherwise never report a result.
   */
  @VisibleForTesting
  public void saveWithoutExecutorAgentsTraces(
      Set<Agent> agentsWithoutExecutor, InjectStatus injectStatus) {
    saveAgentTraces(
        agentsWithoutExecutor,
        injectStatus,
        ExecutionTraceStatus.ERROR,
        agent ->
            "Cannot find the executor for the agent "
                + agent.getExecutedByUser()
                + " from the asset "
                + agent.getAsset().getName());
  }

  /**
   * Writes one terminal AGENT_INACTIVE trace per inactive agent, so the user can tell that the
   * inject was skipped on that agent rather than failed by it.
   */
  @VisibleForTesting
  public void saveInactiveAgentsTraces(Set<Agent> inactiveAgents, InjectStatus injectStatus) {
    saveAgentTraces(
        inactiveAgents,
        injectStatus,
        ExecutionTraceStatus.AGENT_INACTIVE,
        agent ->
            "Agent "
                + agent.getExecutedByUser()
                + " is inactive for the asset "
                + agent.getAsset().getName());
  }

  /**
   * Writes one terminal AGENT_OVERLOADED trace per agent whose job queue exceeds the threshold, so
   * the user can tell that the inject was not dispatched to that agent for capacity reasons.
   */
  @VisibleForTesting
  public void saveOverloadedAgentsTraces(Set<Agent> overloadedAgents, InjectStatus injectStatus) {
    saveAgentTraces(
        overloadedAgents,
        injectStatus,
        ExecutionTraceStatus.AGENT_OVERLOADED,
        agent ->
            "Agent "
                + agent.getExecutedByUser()
                + " is overloaded for the asset "
                + agent.getAsset().getName()
                + " (queue threshold exceeded)");
  }

  /**
   * Writes one terminal ASSET_AGENTLESS trace per targeted asset that has no agent. Asset traces
   * carry no agent, so the asset id is stored as the trace context to attach it to the endpoint.
   */
  @VisibleForTesting
  public void saveAgentlessAssetsTraces(Set<Asset> assetsAgentless, InjectStatus injectStatus) {
    saveAssetTraces(
        assetsAgentless,
        injectStatus,
        ExecutionTraceStatus.ASSET_AGENTLESS,
        asset -> "Asset " + asset.getName() + " has no agent, unable to launch the inject");
  }

  /**
   * Writes one ASSET_CLEARANCE_RESTRICTED trace per agent whose asset is not cleared for the
   * inject's markings, so the endpoint shows "Asset not targeted" instead of a pending status.
   */
  @VisibleForTesting
  public void saveAgentsWithoutClearanceTraces(
      Set<Agent> agentsWithoutClearance, InjectStatus injectStatus) {
    saveAgentTraces(
        agentsWithoutClearance,
        injectStatus,
        ExecutionTraceStatus.ASSET_CLEARANCE_RESTRICTED,
        agent -> ASSET_NOT_TARGETED_MESSAGE);
  }

  /**
   * Agentless counterpart of {@link #saveAgentsWithoutClearanceTraces}: writes one
   * ASSET_CLEARANCE_RESTRICTED trace per non-cleared asset, keyed by the asset id as trace context.
   */
  @VisibleForTesting
  public void saveAssetsWithoutClearanceTraces(
      Set<Asset> assetsWithoutClearance, InjectStatus injectStatus) {
    saveAssetTraces(
        assetsWithoutClearance,
        injectStatus,
        ExecutionTraceStatus.ASSET_CLEARANCE_RESTRICTED,
        asset -> ASSET_NOT_TARGETED_MESSAGE);
  }

  private void saveAgentTraces(
      Set<Agent> agents,
      InjectStatus injectStatus,
      ExecutionTraceStatus status,
      Function<Agent, String> message) {
    if (agents.isEmpty()) {
      return;
    }
    executionTraceRepository.saveAll(
        agents.stream()
            .map(
                agent -> {
                  ExecutionTrace trace =
                      new ExecutionTrace(
                          injectStatus,
                          status,
                          List.of(),
                          message.apply(agent),
                          ExecutionTraceAction.COMPLETE,
                          agent,
                          null);
                  injectStatus.addTrace(trace);
                  return trace;
                })
            .toList());
  }

  private void saveAssetTraces(
      Set<Asset> assets,
      InjectStatus injectStatus,
      ExecutionTraceStatus status,
      Function<Asset, String> message) {
    if (assets.isEmpty()) {
      return;
    }
    executionTraceRepository.saveAll(
        assets.stream()
            .map(
                asset -> {
                  ExecutionTrace trace =
                      new ExecutionTrace(
                          injectStatus,
                          status,
                          List.of(asset.getId()),
                          message.apply(asset),
                          ExecutionTraceAction.COMPLETE,
                          null,
                          null);
                  injectStatus.addTrace(trace);
                  return trace;
                })
            .toList());
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
