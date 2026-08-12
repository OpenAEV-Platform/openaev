package io.openaev.scheduler.jobs;

import static io.openaev.aop.audit_log.AuditEventOrigin.SYSTEM;
import static io.openaev.database.model.CollectExecutionStatus.COMPLETED;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.hasValidResults;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectDependenciesRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ProtectUser;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.helper.InjectHelper;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.scheduler.jobs.exception.ErrorMessagesPreExecutionException;
import io.openaev.service.NotificationEventService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.AgentUtils;
import io.openaev.utils.ExecutionTraceUtils;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class InjectsExecutionJob implements Job {

  public static final int DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES = 10;

  // Thread-safe and expensive to instantiate; never recreate per dependency evaluation
  private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

  @Value("${openaev.notification.simulation-completed-delay-seconds:3600}")
  private long delayForSimulationCompletedEvent;

  @Value(
      "${inject.execution.threshold.minutes:" + DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES + "}")
  private Integer injectExecutionThreshold;

  private final InjectHelper injectHelper;
  private final InjectService injectService;
  private final ExerciseRepository exerciseRepository;
  private final InjectDependenciesRepository injectDependenciesRepository;
  private final InjectExpectationRepository injectExpectationRepository;
  private final ScenarioRepository scenarioRepository;
  private final InjectStatusService injectStatusService;
  private final io.openaev.executors.Executor executor;
  private final ActionMetricCollector actionMetricCollector;
  private final NotificationEventService notificationEventService;
  private final SecurityCoverageSendJobService securityCoverageSendJobService;
  private final EntityManager entityManager;
  private final TenantScopedTransaction tenantTx;

  private final List<ExecutionStatus> executionStatusesNotReady =
      List.of(
          ExecutionStatus.QUEUING,
          ExecutionStatus.DRAFT,
          ExecutionStatus.EXECUTING,
          ExecutionStatus.PENDING);

  private final List<BaseInjectExpectation.EXPECTATION_STATUS> expectationStatusesSuccess =
      List.of(BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);

  private final WorkflowService workflowService;
  private final HealthCheckUtils healthCheckUtils;
  private final Optional<AuditLogger> auditLogger;

  public void handleAutoStartExercises() {
    // Disable tenant filter — called from InjectsExecutionJob which runs cross-tenant
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    List<Exercise> exercises = exerciseRepository.findAllShouldBeInRunningState(now());
    if (exercises.isEmpty()) {
      return;
    }
    actionMetricCollector.addSimulationPlayedCount(exercises.size());
    List<Exercise> startedExercises = new ArrayList<>(exercises);
    startedExercises.forEach(
        exercise -> {
          exercise.setStatus(ExerciseStatus.RUNNING);
          exercise.setUpdatedAt(now());
        });
    exerciseRepository.saveAll(startedExercises);
    startedExercises.forEach(this::logScheduledLaunch);
  }

  public void handleAutoClosingSimulations() {
    // Change status of finished simulations.
    List<Exercise> mustBeFinishedSimulations = exerciseRepository.thatMustBeFinished();
    // Filter out the simulations using the new chaining engine.
    mustBeFinishedSimulations =
        mustBeFinishedSimulations.stream()
            .filter(simulation -> !workflowService.isSimulationChaining(simulation.getId()))
            .toList();
    List<Exercise> exercisesToFinish = new ArrayList<>(mustBeFinishedSimulations);
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
                    != null) // only send notification for exercise associated to a scenario
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

  @VisibleForTesting
  void executeInject(ExecutableInject executableInject) throws Exception {
    // Depending on injector type (internal or external) execution must be done differently
    Inject inject = executableInject.getInjection().getInject();
    // We are now checking if we depend on another inject and if it did not failed
    if (ofNullable(executableInject.getExerciseId()).isPresent()) {
      checkErrorMessagesPreExecution(executableInject.getExerciseId(), inject);
    }
    List<HealthCheck> contentChecks = healthCheckUtils.runContentChecks(inject);
    if (!contentChecks.isEmpty()) {
      String details =
          contentChecks.stream()
              .map(check -> check.getType().getValue() + ":" + check.getDetail().name())
              .distinct()
              .collect(Collectors.joining(", "));
      throw new UnsupportedOperationException(
          "The inject is not ready to be executed (injectId="
              + inject.getId()
              + ", title="
              + inject.getTitle()
              + ", missing mandatory fields: "
              + details
              + ")");
    }
    List<AssetToExecute> resolvedAssets = injectService.resolveAllAssetsToExecute(inject);
    List<Map<String, Object>> endpointResolutions = buildEndpointResolutions(resolvedAssets);
    log.info("Executing inject {}", inject.getInject().getTitle());
    try {
      this.executor.execute(executableInject, resolvedAssets);
    } finally {
      logTargetResolution(inject, executableInject, endpointResolutions);
    }
  }

  /**
   * Get error messages if pre execution conditions are not met
   *
   * @param exerciseId the id of the exercise
   * @param inject the inject to check
   */
  @VisibleForTesting
  protected void checkErrorMessagesPreExecution(String exerciseId, Inject inject)
      throws ErrorMessagesPreExecutionException {
    List<InjectDependency> injectDependencies =
        injectDependenciesRepository.findParents(List.of(inject.getId()));
    if (!injectDependencies.isEmpty()) {
      List<Inject> parents =
          injectDependencies.stream()
              .map(injectDependency -> injectDependency.getCompositeId().getInjectParent())
              .toList();

      Map<String, Boolean> mapCondition =
          getStringBooleanMap(parents, exerciseId, injectDependencies);

      List<String> errorMessages = new ArrayList<>();

      for (InjectDependency injectDependency : injectDependencies) {
        List<String> availableKeys =
            new ArrayList<>(
                StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                            injectDependency
                                .getCompositeId()
                                .getInjectParent()
                                .getContent()
                                .get("expectations")
                                .elements(),
                            0),
                        false)
                    .map(
                        jsonNode -> {
                          if (jsonNode
                              .get("expectation_type")
                              .asText()
                              .equals(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL.name())) {
                            return jsonNode.get("expectation_name").asText().toLowerCase();
                          }
                          return jsonNode.get("expectation_type").asText().toLowerCase();
                        })
                    .toList());
        availableKeys.add("execution");

        if (injectDependency.getInjectDependencyCondition().getConditions().stream()
            .allMatch(condition -> availableKeys.contains(condition.getKey().toLowerCase()))) {
          String expressionToEvaluate = injectDependency.getInjectDependencyCondition().toString();
          List<String> conditions =
              injectDependency.getInjectDependencyCondition().getConditions().stream()
                  .map(InjectDependencyConditions.Condition::toString)
                  .toList();
          for (String condition : conditions) {
            expressionToEvaluate =
                expressionToEvaluate.replaceAll(
                    condition.split("==")[0].trim(),
                    String.format("#this['%s']", condition.split("==")[0].trim()));
          }

          EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
          try {
            Expression exp = SPEL_PARSER.parseExpression(expressionToEvaluate);
            boolean canBeExecuted =
                Boolean.TRUE.equals(exp.getValue(context, mapCondition, Boolean.class));
            if (!canBeExecuted) {
              if (errorMessages.isEmpty()) {
                errorMessages.add(
                    "This inject depends on other injects expectations that are not met. The following conditions were not as expected : ");
              }
              errorMessages.addAll(
                  labelFromCondition(
                      injectDependency.getCompositeId().getInjectParent(),
                      injectDependency.getInjectDependencyCondition()));
            }

          } catch (EvaluationException | SpelParseException e) {
            log.warn(e.getMessage(), e);
            errorMessages.add(
                "There was an error during the evaluation of the condition of the inject");
          }
        } else {
          log.warn("A key in the conditions didn't match any expectations");
          errorMessages.add("A key in the conditions didn't match any expectations");
        }
      }
      if (!errorMessages.isEmpty()) {
        throw new ErrorMessagesPreExecutionException(errorMessages);
      }
    }
  }

  /**
   * Get a map containing the expectations and if they are met or not
   *
   * @param parents the parents injects
   * @param exerciseId the id of the exercise
   * @param injectDependencies the list of dependencies
   * @return a map of expectations and their value
   */
  private @NotNull Map<String, Boolean> getStringBooleanMap(
      List<Inject> parents, String exerciseId, List<InjectDependency> injectDependencies) {
    Map<String, Boolean> mapCondition =
        injectDependencies.stream()
            .flatMap(
                injectDependency ->
                    injectDependency.getInjectDependencyCondition().getConditions().stream())
            .collect(
                Collectors.toMap(InjectDependencyConditions.Condition::getKey, condition -> false));

    parents.forEach(
        parent -> {
          mapCondition.put(
              "Execution",
              parent.getStatus().isPresent()
                  && !ExecutionStatus.ERROR.equals(parent.getStatus().get().getName())
                  && !executionStatusesNotReady.contains(parent.getStatus().get().getName()));

          List<BaseInjectExpectation> expectations =
              injectExpectationRepository.findAllForExerciseAndInject(exerciseId, parent.getId());
          expectations.forEach(
              injectExpectation -> {
                String name =
                    StringUtils.capitalize(injectExpectation.getType().toString().toLowerCase());
                if (injectExpectation
                    .getType()
                    .equals(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL)) {
                  name = injectExpectation.getName();
                }
                if (injectExpectation instanceof TableTopInjectExpectation tableTop
                    && (BaseInjectExpectation.EXPECTATION_TYPE.CHALLENGE.equals(
                            injectExpectation.getType())
                        || BaseInjectExpectation.EXPECTATION_TYPE.ARTICLE.equals(
                            injectExpectation.getType()))) {
                  if (tableTop.getUser() == null && injectExpectation.getScore() != null) {
                    mapCondition.put(
                        name, injectExpectation.getScore() >= injectExpectation.getExpectedScore());
                  }
                } else {
                  mapCondition.put(
                      name, expectationStatusesSuccess.contains(injectExpectation.getResponse()));
                }
              });
        });
    return mapCondition;
  }

  private List<String> labelFromCondition(
      Inject injectParent, InjectDependencyConditions.InjectDependencyCondition condition) {
    List<String> result = new ArrayList<>();
    for (InjectDependencyConditions.Condition conditionElement : condition.getConditions()) {
      result.add(
          String.format(
              "Inject '%s' - %s is %s",
              injectParent.getTitle(), conditionElement.getKey(), conditionElement.isValue()));
    }
    return result;
  }

  public void updateExercise(String exerciseId) {
    Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
    exercise.setUpdatedAt(now());
    exerciseRepository.save(exercise);
  }

  /**
   * Runs an inject execution under BOTH tenant scopes of the platform, set to the inject's tenant:
   * the v2 primitive (transaction GUC, read by the inspector for activated tables such as
   * collectors) and the v1 thread-local {@link TenantContext}, which {@link
   * io.openaev.aop.HibernateFilterTransactionAspect} turns into the Hibernate {@code tenantFilter}
   * on every {@code @Transactional} method it enters.
   *
   * <p>The v1 bridge is not optional: executing an inject resolves asset groups, endpoints and
   * agents through Criteria queries, all still {@code @Filter} entities. {@link
   * TenantContext#getCurrentTenant()} falls back to the DEFAULT tenant when the thread-local is
   * unset, so without this a customer's simulation resolved the default tenant's endpoints and
   * created its expectations against them - cross-tenant rows, and none for the real targets. It
   * stayed invisible in single-tenant deployments, where that fallback happens to be the right
   * tenant. Every other background executor (ScenarioExecutionJob, AtomicTestingExecutionJob,
   * ExpectationsExpirationManagerJob, StepEventService...) already carries the same bridge.
   *
   * <p>Unlike those, this job runs on the shared {@code ForkJoinPool.commonPool} (nested {@code
   * parallelStream}), which also borrows the calling thread: restore the previous value instead of
   * clearing, so the scope of whatever else runs on that thread survives.
   */
  private void executeInTenant(@NotNull final String tenantId, @NotNull final Runnable work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    try {
      // Handle starting exercises if needed.
      handleAutoStartExercises();
      // Get all injects to execute grouped by exercise.
      List<ExecutableInject> injects = injectHelper.getInjectsToRun();

      // Computed once for the whole batch instead of once per inject (was O(n^2))
      Set<String> batchInjectIds =
          injects.stream()
              .map(execInject -> execInject.getInjection().getId())
              .collect(Collectors.toSet());

      // We're grouping the injects to run by exercises but also making sure no injects
      // run in the same batch as it's parents
      Map<String, List<ExecutableInject>> byExercises =
          injects.stream()
              .filter(
                  executableInject ->
                      // If we got dependencies, we check that the parents are not part of the
                      // current batch of injects running. If so, we're filtering them out and
                      // they'll be part of the next batch of launched injects. Do note that this is
                      // an edge case as it's not allowed to add a dependency less than a minute
                      // after a parent but can happen if the platform was restarted after some time
                      // out. It'll then start the injects that were not started because the
                      // platform was down.
                      executableInject.getInjection().getInject().getDependsOn() == null
                          || executableInject.getInjection().getInject().getDependsOn().stream()
                              .map(
                                  injectDependency ->
                                      injectDependency
                                          .getCompositeId()
                                          .getInjectParent()
                                          .getInject()
                                          .getId())
                              .noneMatch(batchInjectIds::contains))
              .collect(
                  groupingBy(
                      ex ->
                          ex.getInjection().getExercise() == null
                              ? "atomic"
                              : ex.getInjection().getExercise().getId()));

      // Execute injects in parallel for each exercise.
      byExercises.entrySet().parallelStream()
          .forEach(
              entry -> {
                // Execute each inject for the exercise in order.
                entry.getValue().parallelStream()
                    .forEach(
                        executableInject -> {
                          Inject inject = executableInject.getInjection().getInject();
                          String tenantId = inject.getTenant().getId();
                          executeInTenant(
                              tenantId,
                              () -> {
                                try {
                                  this.executeInject(executableInject);
                                } catch (RuntimeException e) {
                                  Throwable cause = e.getCause() != null ? e.getCause() : e;
                                  log.warn(cause.getMessage(), cause);
                                  injectStatusService.failInjectStatus(
                                      inject.getId(), cause.getMessage());
                                } catch (Exception e) {
                                  log.warn(e.getMessage(), e);
                                  injectStatusService.failInjectStatus(
                                      inject.getId(), e.getMessage());
                                }
                              });
                        });
                // Update the exercise
                if (!entry.getKey().equals("atomic")) {
                  updateExercise(entry.getKey());
                }
              });
      // Change status of finished simulations.
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
    // Disable tenant filter — called from InjectsExecutionJob which runs cross-tenant
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

  // -- AUDIT LOGGING --

  private void logScheduledLaunch(Exercise exercise) {
    auditLogger.ifPresent(
        logger -> {
          Map<String, Object> contextData = new LinkedHashMap<>();
          contextData.put("simulation_id", exercise.getId());
          contextData.put("simulation_name", exercise.getName());
          contextData.put(
              "scheduled_start", exercise.getStart().map(Instant::toString).orElse(null));
          contextData.put("initiator", "scheduler");
          if (exercise.getScenario() != null) {
            String scenarioId = exercise.getScenario().getId();
            contextData.put("scenario_id", scenarioId);
            // Can't get scenario from exercise here (unproxy exception) so SQL find
            scenarioRepository
                .findNameById(scenarioId)
                .ifPresent(scenarioName -> contextData.put("scenario_name", scenarioName));
          }
          logger.logEvent(
              AuditEvent.builder()
                  .eventType(EventType.SYSTEM)
                  .eventScope(AuditEventScope.SCHEDULED_LAUNCH)
                  .eventStatus(EventStatus.SUCCESS)
                  .resourceType(ResourceType.SIMULATION)
                  .resourceId(exercise.getId())
                  .message(
                      "Simulation '%s' started (scheduled start reached)"
                          .formatted(exercise.getName()))
                  .contextData(contextData)
                  .origin(SYSTEM)
                  .build());
        });
  }

  private void logTargetResolution(
      Inject inject,
      ExecutableInject executableInject,
      List<Map<String, Object>> endpointResolutions) {
    auditLogger.ifPresent(
        logger -> {
          Map<String, Object> contextData = new LinkedHashMap<>();
          contextData.put("inject_id", inject.getId());
          contextData.put("inject_name", inject.getTitle());
          contextData.put(
              "asset_group_ids",
              executableInject.getAssetGroups().stream()
                  .map(AssetGroup::getId)
                  .filter(Objects::nonNull)
                  .toList());
          contextData.put(
              "team_ids",
              executableInject.getTeams().stream()
                  .map(Team::getId)
                  .filter(Objects::nonNull)
                  .toList());
          contextData.put(
              "player_ids",
              executableInject.getUsers().stream()
                  .map(ExecutionContext::getUser)
                  .filter(Objects::nonNull)
                  .map(ProtectUser::getId)
                  .filter(Objects::nonNull)
                  .toList());
          contextData.put("total_endpoints", endpointResolutions.size());
          contextData.put("endpoints", endpointResolutions);
          logger.logEvent(
              AuditEvent.builder()
                  .eventType(EventType.EXECUTION)
                  .eventScope(AuditEventScope.TARGET_RESOLUTION)
                  .eventStatus(EventStatus.SUCCESS)
                  .resourceType(ResourceType.INJECT)
                  .resourceId(inject.getId())
                  .message(
                      "Resolved %d endpoints for inject '%s'"
                          .formatted(endpointResolutions.size(), inject.getTitle()))
                  .contextData(contextData)
                  .origin(SYSTEM)
                  .build());
        });
  }

  private List<Map<String, Object>> buildEndpointResolutions(List<AssetToExecute> resolvedAssets) {
    Map<String, Endpoint> endpointsById = new LinkedHashMap<>();
    resolvedAssets.stream()
        .map(AssetToExecute::asset)
        .filter(Endpoint.class::isInstance)
        .map(Endpoint.class::cast)
        .forEach(endpoint -> endpointsById.putIfAbsent(endpoint.getId(), endpoint));

    return endpointsById.values().stream().map(this::toEndpointResolution).toList();
  }

  private Map<String, Object> toEndpointResolution(Endpoint endpoint) {
    Map<String, Object> endpointResolution = new LinkedHashMap<>();
    endpointResolution.put("endpoint_id", endpoint.getId());

    List<Agent> endpointAgents =
        ofNullable(endpoint.getAgents()).orElse(List.of()).stream()
            .filter(AgentUtils::isPrimaryAgent)
            .toList();
    if (endpointAgents.isEmpty()) {
      endpointResolution.put("status", ExecutionTraceStatus.ASSET_AGENTLESS.name());
      return endpointResolution;
    }

    endpointResolution.put(
        "agents",
        endpointAgents.stream()
            .map(
                agent ->
                    Map.of(
                        "agent_id",
                        agent.getId(),
                        "status",
                        agent.isActive() ? "AGENT_ACTIVE" : "AGENT_INACTIVE"))
            .toList());
    return endpointResolution;
  }
}
