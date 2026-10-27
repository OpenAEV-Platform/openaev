package io.openaev.scheduler.jobs;

import static io.openaev.database.model.CollectExecutionStatus.COMPLETED;
import static io.openaev.scheduler.jobs.InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.hasValidResults;
import static java.time.Instant.now;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.helper.InjectHelper;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectAgentResolverService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.NotificationEventService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.utils.ExecutionTraceUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class InjectsFinalizationJob implements Job {

  @Value("${openaev.notification.simulation-completed-delay-seconds:3600}")
  private long delayForSimulationCompletedEvent;

  @Value(
      "${inject.execution.threshold.minutes:" + DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES + "}")
  private Integer injectExecutionThreshold;

  private final InjectHelper injectHelper;
  private final InjectRepository injectRepository;
  private final InjectService injectService;
  private final InjectStatusService injectStatusService;
  private final ExerciseRepository exerciseRepository;
  private final NotificationEventService notificationEventService;
  private final SecurityCoverageSendJobService securityCoverageSendJobService;
  private final WorkflowService workflowService;
  private final TenantScopedJobRunner tenantScopedJobRunner;
  private final InjectAgentResolverService injectAgentResolverService;

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    try {
      handleInjectExpectationCollectStatus();
      handleAutoClosingSimulations();
      handlePendingInject();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new JobExecutionException(e);
    }
  }

  @VisibleForTesting
  void handleInjectExpectationCollectStatus() {
    // The sweep is cross-tenant and reads under allTenants(). Only the ids leave it: each tenant's
    // injects are refetched, evaluated and saved inside that tenant's scope, like
    // handlePendingInject
    // below. Saving the swept entities instead merges them back, and the merge reloads each
    // inject's
    // injector contract: once that table is tenant-isolated, an unscoped merge cannot load it and
    // throws, which stops execute() before the phases below, for every tenant.
    Map<String, List<String>> collectingIdsByTenant =
        tenantScopedJobRunner.supplyAcrossTenants(
            () -> idsByTenant(injectService.getExecutedAndNotFinished()));
    collectingIdsByTenant.forEach(
        (tenantId, injectIds) ->
            tenantScopedJobRunner.runInTenant(
                tenantId,
                () -> completeFinishedCollections(injectRepository.findAllById(injectIds))));
  }

  @VisibleForTesting
  void completeFinishedCollections(List<Inject> injects) {
    List<Inject> fulfilled = new ArrayList<>();
    for (Inject inject : injects) {
      // An expectation is done collecting when it has nothing to collect (no result
      // placeholders), when every result has been filled, or when its collection window has
      // expired. The expiration escape is critical: partially filled expectations (one collector
      // reported, another never did) keep empty placeholder rows forever and are not picked up by
      // the expiration manager (their score is already set). Without it, a single silent
      // collector leaves the inject COLLECTING and the simulation RUNNING indefinitely.
      boolean collectDone =
          inject.getExpectations().stream()
              .allMatch(
                  expectation -> {
                    // Legacy expectation rows can carry a SQL NULL results column (see
                    // InjectExpectationMapper): treat it as "nothing to collect" instead of
                    // NPE-ing the job and blocking simulation auto-close.
                    List<InjectExpectationResult> results = expectation.getResults();
                    return results == null
                        || results.isEmpty()
                        || hasValidResults(results)
                        || expectation.isExpired();
                  });
      if (collectDone) {
        inject.setCollectExecutionStatus(COMPLETED);
        fulfilled.add(inject);
      }
    }
    injectService.saveAll(fulfilled);
  }

  public void handleAutoClosingSimulations() {
    // Change status of finished simulations. The candidates are read across tenants and only their
    // ids leave that read; each tenant's simulations are then finished in that tenant's scope.
    Map<String, List<String>> simulationIdsByTenant =
        tenantScopedJobRunner.supplyAcrossTenants(
            () -> idsByTenant(exerciseRepository.thatMustBeFinished()));

    simulationIdsByTenant.forEach(
        (tenantId, simulationIds) ->
            tenantScopedJobRunner.runInTenant(
                tenantId,
                () -> {
                  // Leave the simulations of the chaining engine to it. Their workflow lives in
                  // the simulation's tenant, and once workflows is tenant-isolated an unscoped
                  // check finds none, so it runs here, under that tenant's scope.
                  List<String> notChainedIds =
                      simulationIds.stream()
                          .filter(id -> !workflowService.existsBySimulationId(id))
                          .toList();
                  if (notChainedIds.isEmpty()) {
                    return;
                  }
                  // Refetch in tenant scope so eager securityCoverage is resolved under multitenant
                  // v2.
                  List<Exercise> exercisesToFinish =
                      new ArrayList<>(exerciseRepository.findAllById(notChainedIds));
                  exercisesToFinish.forEach(
                      exercise -> {
                        exercise.setStatus(ExerciseStatus.FINISHED);
                        exercise.setEnd(now());
                        exercise.setUpdatedAt(now());
                      });
                  List<Exercise> exercisesFinished = exerciseRepository.saveAll(exercisesToFinish);

                  // maybe trigger stix coverage background job
                  securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(
                      exercisesFinished);

                  // send notification
                  exercisesFinished.stream()
                      .filter(
                          ex ->
                              ex.getScenario()
                                  != null) // only send notification for exercise associated to a
                      // scenario
                      .forEach(
                          ex ->
                              notificationEventService.sendNotificationEventWithDelay(
                                  NotificationEvent.builder()
                                      .eventType(NotificationEventType.SIMULATION_COMPLETED)
                                      .resourceType(ResourceType.SCENARIO)
                                      .resourceId(ex.getScenario().getId())
                                      .timestamp(Instant.now())
                                      .build(),
                                  delayForSimulationCompletedEvent));
                }));
  }

  public void handlePendingInject() {
    // The sweep is cross-tenant and reads under allTenants(). Its sub-query leaves out the injects
    // of a running chaining workflow: once workflows is tenant-isolated, an unscoped sub-query
    // reads nothing, and the sweep would time out the injects the chaining engine owns.
    //
    // Only the ids leave the sweep. Each inject is finalized inside its own tenant's scope, the way
    // handleAutoClosingSimulations does, because resolving its agents walks its assets and asset
    // groups: asset_groups is tenant-isolated, so with no scope AssetGroupService.assetGroup(id)
    // finds nothing and throws, which escapes to execute() and stops the sweep for every tenant
    // (PendingInjectAssetGroupScopeTest). Carrying the entities over instead would keep the eager
    // Inject.assets collection the sweep loaded, which comes back empty once assets is isolated,
    // and the finalization would see an agentless inject where there are agents.
    Map<String, List<String>> pendingIdsByTenant =
        tenantScopedJobRunner.supplyAcrossTenants(
            () ->
                idsByTenant(
                    injectHelper.getAllPendingInjectsWithThresholdMinutes(
                        this.injectExecutionThreshold)));

    pendingIdsByTenant.forEach(
        (tenantId, injectIds) ->
            tenantScopedJobRunner.runInTenant(
                tenantId,
                () ->
                    injectRepository.findAllById(injectIds).forEach(this::finalizePendingInject)));
  }

  private static Map<String, List<String>> idsByTenant(List<? extends TenantBase> rows) {
    Map<String, List<String>> idsByTenant = new LinkedHashMap<>();
    for (TenantBase row : rows) {
      idsByTenant
          .computeIfAbsent(row.getTenant().getId(), key -> new ArrayList<>())
          .add(row.getId());
    }
    return idsByTenant;
  }

  private void finalizePendingInject(Inject inject) {
    InjectStatus status = inject.getStatus().orElseThrow(ElementNotFoundException::new);
    // Find agents that already have a COMPLETE trace
    Set<String> completedAgentIds = ExecutionTraceUtils.getCompletedAgentIds(status.getTraces());

    // Get all agents expected to execute this inject
    List<Agent> allAgents = injectAgentResolverService.getAgentsByInject(inject);

    if (allAgents.isEmpty()) {
      // Agentless inject: network scanners (e.g. Nuclei) target assets that have no agent, so the
      // per-agent timeout loop below can never record anything. Without an explicit trace,
      // updateFinalInjectStatus finalizes the inject ERROR from an empty COMPLETE-trace list and
      // the execution details show only the initial "waiting to be consumed" info trace - a red
      // inject with no reason. Add a clear agentless timeout trace instead, unless a terminal
      // COMPLETE trace was already recorded (e.g. the injector reported the timeout itself).
      boolean hasCompleteTrace =
          status.getTraces().stream()
              .anyMatch(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()));
      if (!hasCompleteTrace) {
        ExecutionTraceUtils.addAgentlessTimeoutTrace(status, this.injectExecutionThreshold);
      }
    } else {
      // Add a COMPLETE/TIMEOUT trace for each agent that never responded
      for (Agent agent : allAgents) {
        if (!completedAgentIds.contains(agent.getId())) {
          ExecutionTraceUtils.addTimeoutTrace(status, agent, this.injectExecutionThreshold);
        }
      }
    }
    injectStatusService.updateFinalInjectStatus(status);
    // Save + stream one by one: the timeout finalization must reach the execution screens in
    // real time (an inject stuck PENDING would otherwise stay "in flight" until a reload).
    injectStatusService.saveAndStreamInject(status);
  }
}
