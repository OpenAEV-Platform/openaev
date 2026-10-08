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
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.scheduler.jobs.exception.ErrorMessagesPreExecutionException;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.payload_approval.BlockedPayloadsException.BlockedPayload;
import io.openaev.service.payload_approval.PayloadApprovalGate;
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
  private final WorkflowService workflowService;
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
  private final PayloadApprovalGate payloadApprovalGate;

  public List<Exercise> autoStartDueExercises() {
    // Disable tenant filter — called from InjectsExecutionJob which runs cross-tenant
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    return promoteDueExercisesToRunning();
  }

  private List<Exercise> promoteDueExercisesToRunning() {
    List<Exercise> exercises = exerciseRepository.findAllShouldBeInRunningState(now());
    if (exercises.isEmpty()) {
      return List.of();
    }
    // A scheduled run that uses a payload which is not approved does not start at all: it goes
    // back to draft (no start date, so the user plans it again on purpose) and the refusal is
    // audited. Normally done as soon as the payload is blocked; this covers any race.
    List<Exercise> startedExercises = new ArrayList<>();
    for (Exercise exercise : exercises) {
      List<BlockedPayload> blocked = payloadApprovalGate.blockedPayloads(exercise.getInjects());
      if (blocked.isEmpty()) {
        startedExercises.add(exercise);
      } else {
        exercise.setStart(null);
        exercise.setUpdatedAt(now());
        exerciseRepository.save(exercise);
        payloadApprovalGate.auditBlocked(
            "Starting the scheduled simulation \"" + exercise.getName() + "\"",
            blocked,
            ResourceType.SIMULATION,
            exercise.getId(),
            SYSTEM);
        log.warn(
            "Scheduled simulation {} moved back to draft: {} payload(s) not approved",
            exercise.getId(),
            blocked.size());
      }
    }
    if (startedExercises.isEmpty()) {
      return List.of();
    }
    actionMetricCollector.addSimulationPlayedCount(startedExercises.size());
    startedExercises.forEach(
        exercise -> {
          exercise.setStatus(ExerciseStatus.RUNNING);
          exercise.setUpdatedAt(now());
        });
    exerciseRepository.saveAll(startedExercises);
    startedExercises.forEach(this::logScheduledLaunch);
    return startedExercises;
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
      // One cross-tenant transaction: the approval check reads each due simulation's injects (a
      // lazy collection) and their payloads (a tenant-scoped table).
      List<Exercise> startedExercises =
          tenantScopedJobRunner.supplyAcrossTenants(this::autoStartDueExercises);
      executeChainedSimulations(startedExercises);
      executeClassicalInjects();
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new JobExecutionException(e);
    }
  }

  private void executeChainedSimulations(List<Exercise> startedExercises) {
    // Chained simulations do not execute injects here; they only start their workflow run.
    startedExercises.forEach(
        exercise -> {
          try {
            workflowService.startWorkflowBySimulationIdIfPresent(exercise.getId());
          } catch (ChainingException e) {
            throw new IllegalStateException(
                "Could not start workflow for scheduled simulation " + exercise.getId(), e);
          }
        });
  }

  private void executeClassicalInjects() throws Exception {
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
                            // Atomic injects have no exercise to group by.
                            ? ATOMIC_BATCH_KEY
                            : ex.getInjection().getExercise().getId()));

    // Classical inject execution only applies to non-chained simulations; chained ones are driven
    // by the workflow engine after the run is created.
    byExercises.entrySet().parallelStream()
        .forEach(
            entry -> {
              entry.getValue().parallelStream()
                  .forEach(
                      executableInject -> {
                        Inject inject = executableInject.getInjection().getInject();
                        String tenantId = inject.getTenant().getId();
                        try {
                          tenantScopedJobRunner.runInTenant(
                              tenantId,
                              () -> {
                                try {
                                  this.executeInject(executableInject);
                                } catch (Exception e) {
                                  // Same transaction: the traces written before the failure and
                                  // the ERROR status commit together.
                                  Throwable cause =
                                      e instanceof RuntimeException && e.getCause() != null
                                          ? e.getCause()
                                          : e;
                                  log.warn(cause.getMessage(), cause);
                                  injectStatusService.persistErrorStatusInTransaction(
                                      inject.getId(), cause.getMessage());
                                }
                              });
                        } catch (RuntimeException e) {
                          // The transaction could not commit (rollback-only, commit or
                          // after-commit failure): persist the ERROR status in a fresh one.
                          Throwable cause = e.getCause() != null ? e.getCause() : e;
                          log.warn(cause.getMessage(), cause);
                          tenantScopedJobRunner.runInTenant(
                              tenantId,
                              () ->
                                  injectStatusService.persistErrorStatusInTransaction(
                                      inject.getId(), cause.getMessage()));
                        }
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
