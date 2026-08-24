package io.openaev.scheduler.jobs;

import static io.openaev.aop.audit_log.AuditEventOrigin.SYSTEM;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;

import com.google.common.annotations.VisibleForTesting;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectDependenciesRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.helper.InjectHelper;
import io.openaev.injector_contract.variables.contract.UserContract;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.scheduler.jobs.exception.ErrorMessagesPreExecutionException;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.AgentUtils;
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

  private static final String ATOMIC_BATCH_KEY = "atomic";

  private final InjectHelper injectHelper;
  private final InjectService injectService;
  private final ExerciseRepository exerciseRepository;
  private final InjectDependenciesRepository injectDependenciesRepository;
  private final InjectExpectationRepository injectExpectationRepository;
  private final ScenarioRepository scenarioRepository;
  private final InjectStatusService injectStatusService;
  private final io.openaev.executors.Executor executor;
  private final ActionMetricCollector actionMetricCollector;
  private final EntityManager entityManager;
  private final TenantScopedJobRunner tenantScopedJobRunner;

  private final List<ExecutionStatus> executionStatusesNotReady =
      List.of(
          ExecutionStatus.QUEUING,
          ExecutionStatus.DRAFT,
          ExecutionStatus.EXECUTING,
          ExecutionStatus.PENDING);

  private final List<BaseInjectExpectation.EXPECTATION_STATUS> expectationStatusesSuccess =
      List.of(BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);

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
    executableInject.cacheAssetsToExecute(resolvedAssets);
    List<Map<String, Object>> endpointResolutions = buildEndpointResolutions(resolvedAssets);
    log.info("Executing inject {}", inject.getInject().getTitle());
    try {
      this.executor.execute(executableInject);
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
                  executableInject -> {
                    Inject inject = executableInject.getInjection().getInject();
                    if (inject.getTenant() != null) {
                      return true;
                    }
                    String message =
                        "Inject " + inject.getId() + " has no tenant, cannot be executed";
                    log.warn(message);
                    injectStatusService.failInjectStatus(inject.getId(), message);
                    return false;
                  })
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
                              // Atomic injects have no exercise to group by.
                              ? ATOMIC_BATCH_KEY
                              : ex.getInjection().getExercise().getId()));

      // Execute exercise batches in parallel. Each inject execution resolves and opens its own
      // tenant scope - a plain field lookup, not a DB call - so nested parallel workers never
      // share or leak tenant context, and the correctness of the scope no longer depends on a
      // batch being single-tenant.
      byExercises.entrySet().parallelStream()
          .forEach(
              entry -> {
                entry.getValue().parallelStream()
                    .forEach(
                        executableInject -> {
                          Inject inject = executableInject.getInjection().getInject();
                          tenantScopedJobRunner.runInTenant(
                              inject.getTenant().getId(),
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

                // Update the exercise once all injects of the batch are processed.
                if (!entry.getKey().equals(ATOMIC_BATCH_KEY)) {
                  entry.getValue().stream()
                      .findFirst()
                      .map(
                          executableInject ->
                              executableInject.getInjection().getInject().getTenant().getId())
                      .ifPresent(
                          tenantId ->
                              tenantScopedJobRunner.runInTenant(
                                  tenantId, () -> updateExercise(entry.getKey())));
                }
              });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new JobExecutionException(e);
    }
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
                  .map(UserContract::getId)
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
