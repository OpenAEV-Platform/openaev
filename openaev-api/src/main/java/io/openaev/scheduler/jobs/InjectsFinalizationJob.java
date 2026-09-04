package io.openaev.scheduler.jobs;

import static io.openaev.database.model.CollectExecutionStatus.COMPLETED;
import static io.openaev.scheduler.jobs.InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.hasValidResults;
import static java.time.Instant.now;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.helper.InjectHelper;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.NotificationEventService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.utils.ExecutionTraceUtils;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
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
  private final InjectService injectService;
  private final InjectStatusService injectStatusService;
  private final ExerciseRepository exerciseRepository;
  private final NotificationEventService notificationEventService;
  private final SecurityCoverageSendJobService securityCoverageSendJobService;
  private final WorkflowService workflowService;
  private final EntityManager entityManager;
  private final TenantScopedJobRunner tenantScopedJobRunner;

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
    // Disable tenant filter — this job runs cross-tenant
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    List<Inject> injects = injectService.getExecutedAndNotFinished();
    if (injects.isEmpty()) {
      return;
    }
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
    // Change status of finished simulations.
    List<Exercise> mustBeFinishedSimulations = exerciseRepository.thatMustBeFinished();
    // Filter out the simulations using the new chaining engine.
    mustBeFinishedSimulations =
        mustBeFinishedSimulations.stream()
            .filter(simulation -> !workflowService.existsBySimulationId(simulation.getId()))
            .toList();
    if (mustBeFinishedSimulations.isEmpty()) {
      return;
    }

    Map<String, List<String>> simulationIdsByTenant = new LinkedHashMap<>();
    mustBeFinishedSimulations.forEach(
        simulation ->
            simulationIdsByTenant
                .computeIfAbsent(simulation.getTenant().getId(), key -> new ArrayList<>())
                .add(simulation.getId()));

    simulationIdsByTenant.forEach(
        (tenantId, simulationIds) ->
            tenantScopedJobRunner.runInTenant(
                tenantId,
                () -> {
                  // Refetch in tenant scope so eager securityCoverage is resolved under multitenant
                  // v2.
                  List<Exercise> exercisesToFinish =
                      new ArrayList<>(exerciseRepository.findAllById(simulationIds));
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
    List<Inject> pendingInjects =
        injectHelper.getAllPendingInjectsWithThresholdMinutes(this.injectExecutionThreshold);

    if (pendingInjects.isEmpty()) {
      return;
    }

    for (Inject inject : pendingInjects) {
      InjectStatus status = inject.getStatus().orElseThrow(ElementNotFoundException::new);
      // Find agents that already have a COMPLETE trace
      Set<String> completedAgentIds = ExecutionTraceUtils.getCompletedAgentIds(status.getTraces());

      // Get all agents expected to execute this inject
      List<Agent> allAgents = injectService.getAgentsByInject(inject);

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
}
